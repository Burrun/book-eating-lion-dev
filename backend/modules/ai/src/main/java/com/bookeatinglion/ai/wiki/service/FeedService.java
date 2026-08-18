package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.lion.domain.GrowthStage;
import com.bookeatinglion.ai.lion.domain.Lion;
import com.bookeatinglion.ai.lion.repository.LionRepository;
import com.bookeatinglion.ai.wiki.domain.FedBook;
import com.bookeatinglion.ai.wiki.domain.WikiBook;
import com.bookeatinglion.ai.wiki.exception.BookNotIngestedException;
import com.bookeatinglion.ai.wiki.repository.FedBookRepository;
import com.bookeatinglion.ai.wiki.repository.WikiBookRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 책 먹이기. 인제스트는 관리자 배치에서 이미 끝나 있으므로 여기서 하는 일은
 * fed_books 행 추가 + exp 갱신 + Redis Set 갱신뿐이라 200ms 안에 끝난다.
 *
 * <p>인제스트를 이 요청 안에서 하지 않는 이유는 책 1권이 12~46청크라 임베딩 + PutVectors 에
 * 수 초~수십 초가 걸리기 때문이다. 동기면 타임아웃이고, 비동기면 진행중/완료/실패
 * 상태 머신과 폴링이 따라온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private final WikiBookRepository wikiBookRepository;
    private final FedBookRepository fedBookRepository;
    private final LionRepository lionRepository;
    private final FedBookCache fedBookCache;

    /**
     * 사자 상태. {@code feed} 와 {@code me} 가 같은 형태를 돌려준다.
     *
     * <p>먹이기 응답도 결국 "먹인 뒤의 사자"라서 조회 응답과 값이 같다. 둘을 따로 두면
     * 같은 개념이 필드 순서만 다른 두 벌로 갈라지고, 프론트는 어느 쪽이 무엇인지 매번
     * 확인해야 한다.
     */
    public record LionStatus(long exp, int level, GrowthStage growthStage, long fedBookCount) {}

    public record FeedableBook(Long bookId, String title, int pages) {}

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
     * 멱등하다. 같은 책을 다시 먹여도 fed_books 의 PK 가 막고 exp 도 중복으로 오르지 않는다 —
     * 재시도나 더블클릭으로 레벨이 오르면 그건 버그가 아니라 사고다.
     */
    @Transactional
    public LionStatus feed(String memberId, Long bookId) {
        if (!wikiBookRepository.existsById(bookId)) {
            throw new BookNotIngestedException(bookId);
        }

        Lion lion = lionRepository.findByMemberId(memberId).orElseGet(() -> lionRepository.save(new Lion(memberId)));

        boolean alreadyFed = fedBookRepository.existsById(new FedBook.Key(memberId, bookId));
        if (!alreadyFed) {
            fedBookRepository.save(new FedBook(memberId, bookId, LocalDateTime.now()));
            lion.gainExp(Lion.EXP_PER_FEED);
            cacheAfterCommit(memberId, bookId);
        }

        return new LionStatus(
                lion.getExp(), lion.getLevel(), lion.getGrowthStage(), fedBookRepository.countByMemberId(memberId));
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

    public List<FeedableBook> feedableBooks(String memberId) {
        return wikiBookRepository.findFeedableFor(memberId).stream()
                .map(FeedService::toFeedable)
                .toList();
    }

    private static FeedableBook toFeedable(WikiBook book) {
        return new FeedableBook(book.getBookId(), book.getTitle(), book.getPages());
    }
}
