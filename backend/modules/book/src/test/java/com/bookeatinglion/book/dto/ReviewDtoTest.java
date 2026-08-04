package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewDtoTest {

    @Test
    void ReviewResponse는_리뷰_필드를_매핑한다() throws Exception {
        Book book = Book.builder()
                .title("제목").author("저자").publisher("출판사").isbn("9791100000031")
                .category("소설").price(10000).stockQuantity(1)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        setField(book, Book.class, "bookId", 10L);

        Review review = Review.builder().book(book).memberId(1L).rating(4).content("괜찮아요").build();
        setField(review, Review.class, "reviewId", 100L);

        ReviewResponse response = ReviewResponse.from(review);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.bookId()).isEqualTo(10L);
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.content()).isEqualTo("괜찮아요");
    }

    private void setField(Object target, Class<?> type, String fieldName, Long value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
