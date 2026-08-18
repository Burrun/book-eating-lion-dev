package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.port.InventoryPort;
import com.bookeatinglion.book.repository.BookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final InventoryPort inventoryPort;

    public Page<BookSummaryResponse> getBooks(String category, Pageable pageable) {
        Page<Book> books = category == null || category.isBlank()
                ? bookRepository.findByIsDeletedFalse(pageable)
                : bookRepository.findByCategoryAndIsDeletedFalse(category, pageable);
        return books.map(BookSummaryResponse::from);
    }

    public Page<BookSummaryResponse> search(String q, Pageable pageable) {
        return bookRepository.search(q, pageable).map(BookSummaryResponse::from);
    }

    public BookDetailResponse getBook(Long bookId) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        // 재고는 order_db 소유라 로컬 조회가 불가능하다. 벌크 계약을 그대로 쓰되 1건만 넘긴다.
        // order-service 장애 시 fallback 이 빈 맵을 주므로 도서 정보는 살아남는다.
        int stock = inventoryPort
                .stockByBookIds(List.of(bookId))
                .getOrDefault(bookId, BookDetailResponse.STOCK_UNAVAILABLE);

        return BookDetailResponse.from(book, stock);
    }

    // 현재 이 메서드의 조회 조건은 limit 뿐이라 key = #limit 만으로 결과를 완전히 특정한다.
    // 이후 카테고리/로케일 등 결과에 영향을 주는 파라미터가 추가되면 key 도 반드시 그만큼
    // 확장해야 한다 — 안 그러면 서로 다른 조회 결과가 같은 캐시 엔트리에서 섞인다.
    @Cacheable(cacheNames = "bestsellers", key = "#limit")
    public List<BookSummaryResponse> getBestsellers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return bookRepository
                .findBySaleStatusAndIsDeletedFalseOrderBySalesCountDesc(SaleStatus.ON_SALE, pageable)
                .stream()
                .map(BookSummaryResponse::from)
                .toList();
    }

    public List<BookSummaryResponse> getNewReleases(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return bookRepository
                .findBySaleStatusAndIsDeletedFalseOrderByPublishedDateDesc(SaleStatus.ON_SALE, pageable)
                .stream()
                .map(BookSummaryResponse::from)
                .toList();
    }

    public BookSynopsisDetailResponse getSynopsisDetail(Long bookId) {
        // TODO: order 모듈 완성 후 구매 인증(해당 회원의 구매 이력) 검증 로직 추가 필요.
        // 현재는 order 모듈에 구매 이력 조회 기능이 없어 인증 체크 없이 상세줄거리를 반환한다.
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        return BookSynopsisDetailResponse.from(book);
    }
}
