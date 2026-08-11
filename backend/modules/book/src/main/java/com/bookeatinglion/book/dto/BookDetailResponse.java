package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookDetailResponse(
        Long id,
        String title,
        String author,
        String publisher,
        String isbn,
        String category,
        int price,
        // catalog_db 가 아니라 order-service 에서 조합해 채운다(API 조합 패턴).
        // order-service 가 죽으면 -1 이 들어오고, 프론트는 재고 영역만 degrade 한다.
        int stockQuantity,
        String coverImageUrl,
        String description,
        SaleStatus saleStatus,
        LocalDate publishedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    /** 재고 조회에 실패했을 때의 표식. 0(품절)과 구분해야 하므로 음수를 쓴다. */
    public static final int STOCK_UNAVAILABLE = -1;

    public static BookDetailResponse from(Book book, int stockQuantity) {
        return new BookDetailResponse(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getIsbn(),
                book.getCategory(),
                book.getPrice(),
                stockQuantity,
                book.getCoverImageUrl(),
                book.getDescription(),
                book.getSaleStatus(),
                book.getPublishedDate(),
                book.getCreatedAt(),
                book.getUpdatedAt());
    }
}
