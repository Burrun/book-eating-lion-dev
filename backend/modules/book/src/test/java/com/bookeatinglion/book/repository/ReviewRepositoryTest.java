package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ReviewRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Book book;

    @BeforeEach
    void setUp() {
        book = bookRepository.save(Book.builder()
                .title("리뷰용 책").author("저자").publisher("출판사").isbn("9791100000021")
                .category("소설").price(10000).stockQuantity(10)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build());
    }

    @Test
    void 리뷰를_저장하고_조회한다() {
        Review review = reviewRepository.save(Review.builder()
                .book(book).memberId(1L).rating(5).content("최고의 책입니다").build());

        Review found = reviewRepository.findById(review.getReviewId()).orElseThrow();

        assertThat(found.getBook().getBookId()).isEqualTo(book.getBookId());
        assertThat(found.getMemberId()).isEqualTo(1L);
        assertThat(found.getRating()).isEqualTo(5);
        assertThat(found.getContent()).isEqualTo("최고의 책입니다");
    }

    @Test
    void 책_id로_리뷰_목록을_페이징_조회한다() {
        reviewRepository.save(Review.builder().book(book).memberId(1L).rating(5).content("리뷰1").build());
        reviewRepository.save(Review.builder().book(book).memberId(2L).rating(3).content("리뷰2").build());

        Page<Review> result = reviewRepository.findByBook_BookId(book.getBookId(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Review::getContent)
                .containsExactlyInAnyOrder("리뷰1", "리뷰2");
    }
}
