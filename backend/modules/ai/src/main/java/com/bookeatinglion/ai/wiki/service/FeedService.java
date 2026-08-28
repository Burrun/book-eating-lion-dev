package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.client.MemberSubscriptionClient;
import com.bookeatinglion.ai.lion.domain.GrowthStage;
import com.bookeatinglion.ai.lion.domain.Lion;
import com.bookeatinglion.ai.lion.repository.LionRepository;
import com.bookeatinglion.ai.wiki.domain.FedBook;
import com.bookeatinglion.ai.wiki.repository.FedBookRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 라이언에게 완독한 책 먹이기. fed_books 행 추가 + exp 갱신 + Redis Set 갱신이 전부다.
 *
 * <p>🔴 <b>여기서 임베딩도 벡터 적재도 하지 않는다.</b> 예전에는 사용자가 쓴 완독 요약 메모를
 * 먹였고 그 텍스트를 벡터로 넣었지만, 이제 먹이는 대상은 책 자체다 — 책 본문 벡터는 관리자
 * 배치(BookIngestService)가 이미 넣어두므로 먹이기는 순수 게이미피케이션(EXP/레벨)이고 외부
 * API 호출이 0회다.
 *
 * <p>"먹일 수 있는 책"의 정의(완독 여부)는 catalog-service(reading_progress)가 안다. 여기는
 * 그걸 판단하지 않는다 — bookId 가 JWT 로 인증된 호출자 본인 것이라는 사실만 믿고 EXP 처리만
 * 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private final FedBookRepository fedBookRepository;
    private final LionRepository lionRepository;
    private final FedBookCache fedBookCache;
    private final MemberSubscriptionClient memberSubscriptionClient;

    /** 구독 회원 EXP 배율. "사자 먹이 2배 적립" 배너 문구와 같은 값이어야 한다. */
    private static final long SUBSCRIBER_EXP_MULTIPLIER = 2;

    /**
     * 사자 상태. {@code feed} 와 {@code me} 가 같은 형태를 돌려준다.
     *
     * <p>먹이기 응답도 결국 "먹인 뒤의 사자"라서 조회 응답과 값이 같다. 둘을 따로 두면
     * 같은 개념이 필드 순서만 다른 두 벌로 갈라지고, 프론트는 어느 쪽이 무엇인지 매번
     * 확인해야 한다.
     */
    public record LionStatus(long exp, int level, GrowthStage growthStage, long fedBookCount) {}

    /**
     * 🔴 <b>조회가 사자를 만들지 않는다.</b> 없으면 기본값을 돌려줄 뿐이다.
     *
     * <p>{@code orElseGet(save(...))} 로 lazy-init 하면 GET 한 번에 {@code lions} 행이 생긴다.
     * 헬스체크·프리페치처럼 훑기만 하는 호출도 상태를 만들게 되고, 조회 메서드가
     * 쓰기 트랜잭션을 요구하게 된다.
     *
     * <p>사자는 {@link #feed} 에서 생긴다. 먹이기 전의 사자는 "레벨 1 / 경험치 0" 이고,
     * 그건 행이 없는 것과 같은 의미다.
     */
    public LionStatus getLionStatus(String memberId) {
        long fedBookCount = fedBookRepository.countByMemberId(memberId);
        return lionRepository
                .findByMemberId(memberId)
                .map(lion -> new LionStatus(lion.getExp(), lion.getLevel(), lion.getGrowthStage(), fedBookCount))
                .orElseGet(() -> new LionStatus(0, 1, GrowthStage.BABY, fedBookCount));
    }

    /**
     * EXP는 멱등하다 — 같은 책을 다시 먹여도 fed_books 의 PK 가 막아 중복으로 오르지 않는다
     * (재시도·더블클릭으로 레벨이 또 오르면 그건 버그다).
     */
    @Transactional
    public LionStatus feed(String memberId, Long bookId) {
        Lion lion = lionRepository.findByMemberId(memberId).orElseGet(() -> lionRepository.save(new Lion(memberId)));

        boolean alreadyFed = fedBookRepository.existsById(new FedBook.Key(memberId, bookId));
        if (!alreadyFed) {
            fedBookRepository.save(new FedBook(memberId, bookId, LocalDateTime.now()));
            lion.gainExp(Lion.EXP_PER_FEED * expMultiplier(memberId));
            cacheAfterCommit(memberId, bookId);
        }

        return new LionStatus(
                lion.getExp(), lion.getLevel(), lion.getGrowthStage(), fedBookRepository.countByMemberId(memberId));
    }

    /**
     * 구독 중이면 2배, 조회 실패(장애/타임아웃)면 1배 — {@link MemberSubscriptionClientFallback}가
     * 이미 안전 강등해서 돌려주므로 여기서 예외를 따로 잡지 않는다.
     */
    private long expMultiplier(String memberId) {
        boolean subscribed =
                memberSubscriptionClient.getSubscriptionStatus(memberId).subscribed();
        return subscribed ? SUBSCRIBER_EXP_MULTIPLIER : 1;
    }

    /**
     * 🔴 커밋 후에만 캐시에 넣는다. 트랜잭션 안에서 넣으면 롤백 시 fed_books 에는 행이
     * 없는데 Redis Set 에는 남아, 사자가 먹지도 않은 책을 먹은 것으로 세게 된다.
     *
     * <p>캐시 쓰기 자체가 실패하는 방향은 안전하다. 원본은 DB 에 있으므로 다음 조회에서
     * {@link FedBookCache} 가 다시 채운다.
     */
    private void cacheAfterCommit(String memberId, Long bookId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fedBookCache.add(memberId, bookId);
                }
            });
        } else {
            fedBookCache.add(memberId, bookId);
        }
    }
}
