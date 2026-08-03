package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RecentViewedBook;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.RecentViewedBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentViewedBookService {

    private final RecentViewedBookRepository recentViewedBookRepository;
    private final BookRepository bookRepository;

    @Transactional
    public void recordView(Long bookId, Long memberId) {
        recentViewedBookRepository.findByMemberIdAndBookId(memberId, bookId).ifPresentOrElse(
                existing -> existing.touch(LocalDateTime.now()), // 영속 상태 엔티티라 dirty checking으로 자동 UPDATE, save() 불필요
                () -> {
                    Book book = bookRepository.findById(bookId)
                            .orElseThrow(() -> new BookNotFoundException(bookId));
                    recentViewedBookRepository.save(RecentViewedBook.builder()
                            .memberId(memberId)
                            .book(book)
                            .viewedAt(LocalDateTime.now())
                            .build());
                });
    }

    public List<BookSummaryResponse> getMyRecentBooks(Long memberId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return recentViewedBookRepository.findByMemberIdOrderByViewedAtDesc(memberId, pageable).stream()
                .map(recentViewedBook -> BookSummaryResponse.from(recentViewedBook.getBook()))
                .toList();
    }
}
