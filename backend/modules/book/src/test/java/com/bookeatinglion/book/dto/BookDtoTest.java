package com.bookeatinglion.book.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BookDtoTest {

    private Book sampleBook() {
        return Book.builder()
                .title("제목")
                .author("저자")
                .publisher("출판사")
                .isbn("9791100000001")
                .category("소설")
                .price(15000)
                .coverImageUrl("https://example.com/cover.jpg")
                .description("짧은 소개")
                .detailedSynopsis("상세 줄거리")
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.of(2026, 5, 1))
                .salesCount(9)
                .build();
    }

    @Test
    void BookSummaryResponse는_요약_필드만_매핑한다() {
        BookSummaryResponse response = BookSummaryResponse.from(sampleBook());

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.author()).isEqualTo("저자");
        assertThat(response.price()).isEqualTo(15000);
        assertThat(response.coverImageUrl()).isEqualTo("https://example.com/cover.jpg");
        assertThat(response.category()).isEqualTo("소설");
        assertThat(response.saleStatus()).isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    void BookDetailResponse는_상세_필드를_매핑한다() {
        // 재고는 Book 이 아니라 order-service 에서 조합해 넘어온다.
        BookDetailResponse response = BookDetailResponse.from(sampleBook(), 7);

        assertThat(response.publisher()).isEqualTo("출판사");
        assertThat(response.isbn()).isEqualTo("9791100000001");
        assertThat(response.stockQuantity()).isEqualTo(7);
        assertThat(response.description()).isEqualTo("짧은 소개");
        assertThat(response.publishedDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void BookSynopsisDetailResponse는_상세줄거리를_매핑한다() {
        BookSynopsisDetailResponse response = BookSynopsisDetailResponse.from(sampleBook());

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.detailedSynopsis()).isEqualTo("상세 줄거리");
    }
}
