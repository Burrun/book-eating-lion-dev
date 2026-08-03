package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;

    public Page<BookSummaryResponse> getBooks(String category, Pageable pageable) {
        Page<Book> books = (category == null || category.isBlank())
                ? bookRepository.findAll(pageable)
                : bookRepository.findByCategory(category, pageable);
        return books.map(BookSummaryResponse::from);
    }

    public Page<BookSummaryResponse> search(String q, Pageable pageable) {
        return bookRepository.search(q, pageable).map(BookSummaryResponse::from);
    }

    public BookDetailResponse getBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        return BookDetailResponse.from(book);
    }

    public List<BookSummaryResponse> getBestsellers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return bookRepository.findBySaleStatusOrderBySalesCountDesc(SaleStatus.ON_SALE, pageable)
                .stream()
                .map(BookSummaryResponse::from)
                .toList();
    }

    public List<BookSummaryResponse> getNewReleases(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return bookRepository.findBySaleStatusOrderByPublishedDateDesc(SaleStatus.ON_SALE, pageable)
                .stream()
                .map(BookSummaryResponse::from)
                .toList();
    }

    public BookSynopsisDetailResponse getSynopsisDetail(Long bookId) {
        // TODO: order 모듈 완성 후 구매 인증(해당 회원의 구매 이력) 검증 로직 추가 필요.
        // 현재는 order 모듈에 구매 이력 조회 기능이 없어 인증 체크 없이 상세줄거리를 반환한다.
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        return BookSynopsisDetailResponse.from(book);
    }
}
