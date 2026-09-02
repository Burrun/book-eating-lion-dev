package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ReadingProgress;
import com.bookeatinglion.book.dto.FeedableBookResponse;
import com.bookeatinglion.book.dto.ReadingProgressRequest;
import com.bookeatinglion.book.dto.ReadingProgressResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReadingProgressRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReadingProgressService {

    private final ReadingProgressRepository readingProgressRepository;
    private final BookRepository bookRepository;

    /**
     * 완독 판정 임계값. epub.js 의 atEnd(파일의 물리적 끝)만으로 판정하면, 구텐베르크 소스
     * EPUB 처럼 본문 뒤에 라이선스 고지문이 같은 스파인 항목에 이어붙은 책은 사용자가 그
     * 고지문까지 넘기지 않는 한 영영 100%가 안 된다(프랑켄슈타인/이상한 나라의 앨리스 둘 다
     * 이 구조다). 라이선스 재배포 요건상 EPUB 은 못 고치므로 "사실상 다 읽었다"고 볼 수 있는
     * 선에서 끊는다. 책마다 꼬리 길이가 달라 조절이 필요한 값이라 설정으로 뺀다.
     */
    private final int completionPercentage;

    public ReadingProgressService(
            ReadingProgressRepository readingProgressRepository,
            BookRepository bookRepository,
            @Value("${catalog.reading.completion-percentage:95}") int completionPercentage) {
        this.readingProgressRepository = readingProgressRepository;
        this.bookRepository = bookRepository;
        this.completionPercentage = completionPercentage;
    }

    /**
     * 회원×도서 단위 upsert. 이력은 남기지 않고 최신 위치/퍼센트만 유지한다
     * (RecentViewedBookService.recordView()와 동일한 모양).
     */
    @Transactional
    public ReadingProgressResponse saveProgress(Long bookId, String memberSub, ReadingProgressRequest request) {
        // 이미 기록이 있어도 도서가 그 사이 삭제됐을 수 있으니, 분기와 무관하게 먼저 검증한다.
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        ReadingProgress readingProgress = readingProgressRepository
                .findByMemberSubAndBook_BookId(memberSub, bookId)
                .map(existing -> {
                    existing.updateProgress(request.cfi(), request.percentage());
                    return existing;
                })
                .orElseGet(() -> readingProgressRepository.save(ReadingProgress.builder()
                        .memberSub(memberSub)
                        .book(book)
                        .cfi(request.cfi())
                        .percentage(request.percentage())
                        .build()));
        return ReadingProgressResponse.from(readingProgress);
    }

    /** 기록이 없으면 null을 반환한다 — 한 번도 안 읽은 책은 정상 상태라 예외가 아니다. */
    public ReadingProgressResponse getProgress(Long bookId, String memberSub) {
        if (!bookRepository.existsByBookIdAndIsDeletedFalse(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        return readingProgressRepository
                .findByMemberSubAndBook_BookId(memberSub, bookId)
                .map(ReadingProgressResponse::from)
                .orElse(null);
    }

    /** 마이페이지 사자 먹이기 카드 — 완독했지만 아직 안 먹인 책만. */
    public List<FeedableBookResponse> listFeedableBooks(String memberSub) {
        return readingProgressRepository.findFeedable(memberSub, completionPercentage).stream()
                .map(FeedableBookResponse::from)
                .toList();
    }

    /**
     * ai-service feed 호출이 성공한 뒤 프론트가 부른다(두 서비스를 동기로 엮지 않고 프론트가
     * 오케스트레이션한다). 멱등하다 — 이미 fedAt 이 있어도 시각만 덮어쓸 뿐 오류가 아니다.
     */
    @Transactional
    public void markFed(Long bookId, String memberSub) {
        ReadingProgress readingProgress = readingProgressRepository
                .findByMemberSubAndBook_BookId(memberSub, bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "먹이기 완료 처리할 독서 기록이 없습니다: memberSub=%s bookId=%d".formatted(memberSub, bookId)));
        readingProgress.markFed(LocalDateTime.now());
    }
}
