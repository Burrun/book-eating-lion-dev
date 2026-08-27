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

    // 고객용 목록/검색에서 빼는 상태. OUT_OF_STOCK 은 남긴다 — 품절이어도 상세로 들어가
    // 재입고 알림을 신청할 수 있어야 하고, 그게 restock_alerts 의 유일한 진입점이다.
    //
    // 이 필터가 정기구독권(90-demo-data.sql 의 book_id 9001, sale_status=STOPPED)도 같이
    // 걷어낸다. 구독권은 결제 경로를 도서와 공유하려고 books 한 행으로 넣은 것이라
    // (frontend/src/constants/subscription.ts 참고) 카탈로그를 그냥 조회하면 "책" 인 척
    // 목록에 섞여 나왔다. 구독권 id 를 여기서 다시 상수로 들고 있으면 그 값이 어긋날 자리가
    // 하나 더 생기므로, id 가 아니라 판매 상태로 거른다.
    private static final SaleStatus HIDDEN_FROM_CATALOG = SaleStatus.STOPPED;

    public Page<BookSummaryResponse> getBooks(String category, Boolean hasEbook, Pageable pageable) {
        // 전자책 필터는 장르(category)와 별개 축이라 배타적으로 처리한다 — 두 조건을 함께
        // 걸면 그 2권이 원래 장르 탭에서 사라진 것처럼 보이는 혼란을 피하려는 의도다.
        Page<Book> books;
        if (Boolean.TRUE.equals(hasEbook)) {
            books = bookRepository.findByEpubS3KeyIsNotNullAndSaleStatusNotAndIsDeletedFalse(
                    HIDDEN_FROM_CATALOG, pageable);
        } else if (category == null || category.isBlank()) {
            books = bookRepository.findBySaleStatusNotAndIsDeletedFalse(HIDDEN_FROM_CATALOG, pageable);
        } else {
            books = bookRepository.findByCategoryAndSaleStatusNotAndIsDeletedFalse(
                    category, HIDDEN_FROM_CATALOG, pageable);
        }
        return books.map(BookSummaryResponse::from);
    }

    public Page<BookSummaryResponse> search(String q, Pageable pageable) {
        return bookRepository.search(q, HIDDEN_FROM_CATALOG, pageable).map(BookSummaryResponse::from);
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
