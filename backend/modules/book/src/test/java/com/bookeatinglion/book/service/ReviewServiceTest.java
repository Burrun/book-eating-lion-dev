package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.ReviewRequest;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private ReviewService reviewService;

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책").author("저자").publisher("출판사").isbn("978110000" + id)
                .category("소설").price(10000).stockQuantity(5)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        setId(book, Book.class, id);
        return book;
    }

    private Review review(Long id, Book book, Long memberId) throws Exception {
        Review review = Review.builder().book(book).memberId(memberId).rating(5).content("내용").build();
        setId(review, Review.class, id);
        return review;
    }

    private void setId(Object target, Class<?> type, Long id) throws Exception {
        Field idField = type.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }

    @Test
    void 존재하는_책의_리뷰_목록을_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        Book book = book(1L);
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.findByBookId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(review(100L, book, 1L))));

        Page<ReviewResponse> result = reviewService.getReviews(1L, pageable);

        assertThat(result.getContent()).extracting(ReviewResponse::content).containsExactly("내용");
    }

    @Test
    void 존재하지_않는_책의_리뷰_목록_조회는_예외를_던진다() {
        when(bookRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.getReviews(999L, PageRequest.of(0, 10)))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 리뷰를_생성한다() throws Exception {
        Book book = book(1L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            setId(saved, Review.class, 100L);
            return saved;
        });

        ReviewResponse result = reviewService.createReview(1L, 1L, new ReviewRequest(5, "최고예요"));

        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.content()).isEqualTo("최고예요");
        assertThat(result.memberId()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_책에_리뷰_생성은_예외를_던진다() {
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(999L, 1L, new ReviewRequest(5, "내용")))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 작성자_본인이_리뷰를_삭제한다() throws Exception {
        Book book = book(1L);
        Review review = review(100L, book, 1L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(100L, 1L);

        verify(reviewRepository, times(1)).delete(review);
    }

    @Test
    void 존재하지_않는_리뷰_삭제는_예외를_던진다() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(999L, 1L))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    void 작성자가_아니면_리뷰_삭제시_예외를_던진다() throws Exception {
        Book book = book(1L);
        Review review = review(100L, book, 1L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(100L, 999L))
                .isInstanceOf(ReviewAccessDeniedException.class);
        verify(reviewRepository, never()).delete(any());
    }
}
