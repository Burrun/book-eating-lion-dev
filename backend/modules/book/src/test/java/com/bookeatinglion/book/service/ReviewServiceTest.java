package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.ReviewRequest;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.dto.ReviewUpdateRequest;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.exception.ReviewPermissionRequiredException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewPermissionRepository;
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

    /**
     * 구매 검증은 order-service 호출이 아니라 이 로컬 테이블 조회다.
     * 그래서 이 테스트에 order 관련 목이 하나도 없다.
     */
    @Mock
    private ReviewPermissionRepository reviewPermissionRepository;

    @InjectMocks
    private ReviewService reviewService;

    private ReviewPermission permission(Long memberId, Long bookId) {
        return new ReviewPermission(memberId, 500L, bookId, "테스트유저", java.time.LocalDateTime.now());
    }

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책").author("저자").publisher("출판사").isbn("978110000" + id)
                .category("소설").price(10000)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        setField(book, Book.class, "bookId", id);
        return book;
    }

    private Review review(Long id, Book book, Long memberId) throws Exception {
        Review review = Review.builder().book(book).memberId(memberId).orderItemId(500L)
                .rating(5).content("내용").build();
        setField(review, Review.class, "reviewId", id);
        return review;
    }

    private void setField(Object target, Class<?> type, String fieldName, Long value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void 존재하는_책의_리뷰_목록을_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        Book book = book(1L);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(reviewRepository.findByBook_BookId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(review(100L, book, 1L))));

        Page<ReviewResponse> result = reviewService.getReviews(1L, pageable);

        assertThat(result.getContent()).extracting(ReviewResponse::content).containsExactly("내용");
    }

    @Test
    void 관리자는_삭제_여부와_관계없이_책의_리뷰를_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        Book book = book(1L);
        book.delete(java.time.LocalDateTime.now());
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.findByBook_BookId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(review(100L, book, 1L))));

        Page<ReviewResponse> result = reviewService.getAdminReviews(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_책의_리뷰_목록_조회는_예외를_던진다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getReviews(999L, PageRequest.of(0, 10)))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 사전_발급된_권한이_있으면_리뷰를_생성한다() throws Exception {
        Book book = book(1L);
        ReviewPermission permission = permission(1L, 1L);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(reviewPermissionRepository.findFirstByIdMemberIdAndBookIdAndUsedAtIsNull(1L, 1L))
                .thenReturn(Optional.of(permission));
        when(reviewRepository.findAverageRatingByBookId(1L)).thenReturn(5.0);
        when(reviewRepository.countByBook_BookId(1L)).thenReturn(1L);
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            setField(saved, Review.class, "reviewId", 100L);
            return saved;
        });

        ReviewResponse result = reviewService.createReview(1L, 1L, new ReviewRequest(5, "최고예요"));

        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.content()).isEqualTo("최고예요");
        assertThat(result.memberId()).isEqualTo(1L);
        // 닉네임은 members 조인이 아니라 이벤트로 받은 스냅샷에서 온다.
        assertThat(result.nickname()).isEqualTo("테스트유저");
        // 1건당 1리뷰 — 권한이 소진됐다.
        assertThat(permission.isUsed()).isTrue();
        assertThat(book.getAverageRating()).isEqualByComparingTo("5.00");
        assertThat(book.getReviewCount()).isEqualTo(1);
    }

    @Test
    void 구매_확정_이력이_없으면_리뷰_생성이_거부된다() throws Exception {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book(1L)));
        when(reviewPermissionRepository.findFirstByIdMemberIdAndBookIdAndUsedAtIsNull(1L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(1L, 1L, new ReviewRequest(5, "내용")))
                .isInstanceOf(ReviewPermissionRequiredException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_책에_리뷰_생성은_예외를_던진다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(999L, 1L, new ReviewRequest(5, "내용")))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 작성자_본인이_리뷰를_삭제한다() throws Exception {
        Book book = book(1L);
        Review review = review(100L, book, 1L);
        ReviewPermission permission = permission(1L, 1L);
        permission.markUsed(java.time.LocalDateTime.now());
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(reviewPermissionRepository.findById(any())).thenReturn(Optional.of(permission));
        when(reviewRepository.findAverageRatingByBookId(1L)).thenReturn(0.0);
        when(reviewRepository.countByBook_BookId(1L)).thenReturn(0L);

        reviewService.deleteReview(100L, 1L);

        verify(reviewRepository, times(1)).delete(review);
        assertThat(permission.isUsed()).isFalse();
        assertThat(book.getAverageRating()).isEqualByComparingTo("0.00");
        assertThat(book.getReviewCount()).isZero();
    }

    @Test
    void 작성자_본인이_리뷰를_수정하면_평점과_내용이_변경된다() throws Exception {
        Book book = book(1L);
        Review review = review(100L, book, 1L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(reviewRepository.findAverageRatingByBookId(1L)).thenReturn(4.0);
        when(reviewRepository.countByBook_BookId(1L)).thenReturn(1L);

        ReviewResponse result = reviewService.updateReview(
                100L, 1L, new ReviewUpdateRequest(4, "수정한 내용"));

        assertThat(result.rating()).isEqualTo(4);
        assertThat(result.content()).isEqualTo("수정한 내용");
        assertThat(book.getAverageRating()).isEqualByComparingTo("4.00");
        assertThat(book.getReviewCount()).isEqualTo(1);
    }

    @Test
    void 작성자가_아니면_리뷰_수정은_거부된다() throws Exception {
        Review review = review(100L, book(1L), 1L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(
                100L, 2L, new ReviewUpdateRequest(4, "수정")))
                .isInstanceOf(ReviewAccessDeniedException.class);
    }

    @Test
    void 삭제된_책의_리뷰는_수정할_수_없다() throws Exception {
        Book book = book(1L);
        book.delete(java.time.LocalDateTime.now());
        Review review = review(100L, book, 1L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(
                100L, 1L, new ReviewUpdateRequest(4, "수정")))
                .isInstanceOf(BookNotFoundException.class);
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
