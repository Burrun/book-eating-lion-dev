package com.bookeatinglion.ai.wiki.service;

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

    /** lions.growth_stage 는 NOT NULL 이다. 첫 라이언의 초기값. */
    private static final String INITIAL_GROWTH_STAGE = "CUB";

    private final WikiBookRepository wikiBookRepository;
    private final FedBookRepository fedBookRepository;
    private final LionRepository lionRepository;
    private final FedBookCache fedBookCache;

    public record FeedResult(long exp, int level, long fedBookCount) {}

    public record FeedableBook(Long bookId, String title, int pages) {}

    /**
     * 멱등하다. 같은 책을 다시 먹여도 fed_books 의 PK 가 막고 exp 도 중복으로 오르지 않는다 —
     * 재시도나 더블클릭으로 레벨이 오르면 그건 버그가 아니라 사고다.
     */
    @Transactional
    public FeedResult feed(Long memberId, Long bookId) {
        if (!wikiBookRepository.existsById(bookId)) {
            throw new BookNotIngestedException(bookId);
        }

        Lion lion = lionRepository
                .findByMemberId(memberId)
                .orElseGet(() -> lionRepository.save(new Lion(memberId, INITIAL_GROWTH_STAGE)));

        boolean alreadyFed = fedBookRepository.existsById(new FedBook.Key(memberId, bookId));
        if (!alreadyFed) {
            fedBookRepository.save(new FedBook(memberId, bookId, LocalDateTime.now()));
            lion.gainExp(Lion.EXP_PER_FEED);
            // 트랜잭션 밖에서 실패해도 원본은 DB 에 있다. 다음 질의에서 캐시가 다시 채워진다.
            fedBookCache.add(memberId, bookId);
        }

        return new FeedResult(lion.getExp(), lion.getLevel(), fedBookRepository.countByMemberId(memberId));
    }

    public List<FeedableBook> feedableBooks(Long memberId) {
        return wikiBookRepository.findFeedableFor(memberId).stream()
                .map(FeedService::toFeedable)
                .toList();
    }

    private static FeedableBook toFeedable(WikiBook book) {
        return new FeedableBook(book.getBookId(), book.getTitle(), book.getPages());
    }
}
