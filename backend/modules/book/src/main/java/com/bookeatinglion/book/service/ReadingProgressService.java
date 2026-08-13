package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ReadingProgress;
import com.bookeatinglion.book.dto.ReadingProgressRequest;
import com.bookeatinglion.book.dto.ReadingProgressResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingProgressService {

    private final ReadingProgressRepository readingProgressRepository;
    private final BookRepository bookRepository;

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
}
