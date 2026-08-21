package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.client.EmbeddingClient;
import com.bookeatinglion.ai.client.MemberSubscriptionClient;
import com.bookeatinglion.ai.lion.domain.GrowthStage;
import com.bookeatinglion.ai.lion.domain.Lion;
import com.bookeatinglion.ai.lion.repository.LionRepository;
import com.bookeatinglion.ai.wiki.domain.FedBook;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort.VectorRecord;
import com.bookeatinglion.ai.wiki.repository.FedBookRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 라이언에게 메모 먹이기. 완독 후 사용자가 쓴 요약 메모 텍스트를 받아 임베딩 → 벡터 적재까지
 * 동기로 끝내고, fed_books 행 추가 + exp 갱신 + Redis Set 갱신을 더한다.
 *
 * <p>🔴 <b>책 본문 인제스트(BookIngestService)와는 다른 결정이다.</b> 그쪽은 책 1권이
 * 12~46청크라 동기로 하면 타임아웃이 나서 관리자 배치(비동기)가 필수였지만, 메모는 텍스트
 * 하나 = 벡터 하나라 임베딩 1회로 끝나 동기로도 충분히 빠르다(수 초 이내) — 팀에서 이렇게
 * 가기로 확정했다(느리면 프론트가 로딩 표시로 흡수한다).
 *
 * <p>"먹일 수 있는 메모"의 정의(완독 + 메모 작성)는 이제 catalog-service(book_memos)가
 * 안다. 여기는 더 이상 그걸 판단하지 않는다 — bookId/memoText가 JWT로 인증된 호출자 본인
 * 것이라는 사실만 믿고 임베딩·적재·EXP 처리만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private final FedBookRepository fedBookRepository;
    private final LionRepository lionRepository;
    private final FedBookCache fedBookCache;
    private final EmbeddingClient embedding;
    private final VectorIndexPort vectorIndex;
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
     * (재시도·더블클릭·재작성 재-feed로 레벨이 또 오르면 그건 버그다).
     *
     * <p>벡터 적재는 멱등이 아니라 <b>항상 최신으로 덮어쓴다</b> — 메모를 완독 후 다시 고쳐
     * 쓴 사용자가 재작성 뒤 다시 먹이면(EXP는 이미 받았으니 안 오르지만) RAG가 아는 내용은
     * 최신 텍스트여야 하기 때문이다. 벡터 키가 회원×도서로 결정적이라(멱등 키) 재적재가
     * 곧 갱신이다.
     */
    @Transactional
    public LionStatus feed(String memberId, Long bookId, String bookTitle, String memoText) {
        float[] vector = embedding.embed(memoText);
        vectorIndex.put(List.of(new VectorRecord(
                VectorIndexPort.memoKey(bookId, memberId),
                vector,
                bookId,
                bookTitle,
                "",
                1,
                memoText,
                VectorIndexPort.SOURCE_USER_SUMMARY,
                memberId)));

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
        boolean subscribed = memberSubscriptionClient.getSubscriptionStatus(memberId).subscribed();
        return subscribed ? SUBSCRIBER_EXP_MULTIPLIER : 1;
    }

    /**
     * 🔴 커밋 후에만 캐시에 넣는다. 트랜잭션 안에서 넣으면 롤백 시 fed_books 에는 행이
     * 없는데 Redis Set 에는 남고, 그 목록이 그대로 검색 필터라서 안 먹은 책의 본문이
     * 인용까지 나간다 — 예외도 로그도 남지 않는 접근 제어 사고다.
     *
     * <p>캐시 쓰기 자체가 실패하는 방향은 안전하다. 원본은 DB 에 있으므로 다음 질의에서
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
