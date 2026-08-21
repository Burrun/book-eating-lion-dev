package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.domain.ReviewPermissionId;
import com.bookeatinglion.book.dto.MemberReviewResponse;
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
import com.bookeatinglion.book.repository.ReviewStatistics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;
    private final ReviewPermissionRepository reviewPermissionRepository;

    public Page<ReviewResponse> getReviews(Long bookId, Pageable pageable) {
        if (bookRepository.findByBookIdAndIsDeletedFalse(bookId).isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        return reviewRepository.findByBook_BookId(bookId, pageable).map(ReviewResponse::from);
    }

    public Page<ReviewResponse> getAdminReviews(Long bookId, Pageable pageable) {
        if (!bookRepository.existsById(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        return reviewRepository.findByBook_BookId(bookId, pageable).map(ReviewResponse::from);
    }

    public List<MemberReviewResponse> getMyReviews(String memberId) {
        return reviewRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(MemberReviewResponse::from)
                .toList();
    }

    /**
     * 구매 검증을 order-service 에 묻지 않는다. 구매 확정 시점에 이벤트로 미리 받아둔
     * review_permissions 를 자기 DB 에서 조회할 뿐이다.
     *
     * 그래서 이 메서드 전체가 단일 DB 로컬 트랜잭션이다 — 권한 소진(usedAt)과
     * 리뷰 저장이 같이 커밋되므로 Saga 가 필요 없고, order-service 가 죽어 있어도
     * 정상 동작한다.
     */
    @Transactional
    public ReviewResponse createReview(Long bookId, String memberId, ReviewRequest request) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        ReviewPermission permission = reviewPermissionRepository
                .findFirstByIdMemberIdAndBookIdAndUsedAtIsNull(memberId, bookId)
                .orElseThrow(() -> new ReviewPermissionRequiredException(memberId, bookId));

        permission.markUsed(LocalDateTime.now());

        Review review = Review.builder()
                .book(book)
                .memberId(memberId)
                .orderItemId(permission.getId().getOrderItemId())
                .nickname(permission.getNickname())
                .rating(request.rating())
                .content(request.content())
                .build();
        Review savedReview = reviewRepository.save(review);
        updateReviewStatistics(book);
        return ReviewResponse.from(savedReview);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, String memberId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new ReviewNotFoundException(reviewId));
        if (!review.getMemberId().equals(memberId)) {
            throw new ReviewAccessDeniedException(reviewId, memberId);
        }

        Book book = review.getBook();
        if (book.isDeleted()) {
            throw new BookNotFoundException(book.getBookId());
        }

        review.update(request.rating(), request.content());
        updateReviewStatistics(book);
        return ReviewResponse.from(review);
    }

    @Transactional
    public void deleteReview(Long reviewId, String memberId) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new ReviewNotFoundException(reviewId));
        if (!review.getMemberId().equals(memberId)) {
            throw new ReviewAccessDeniedException(reviewId, memberId);
        }

        if (review.getOrderItemId() != null) {
            reviewPermissionRepository
                    .findById(new ReviewPermissionId(memberId, review.getOrderItemId()))
                    .ifPresent(ReviewPermission::restore);
        }

        Book book = review.getBook();
        reviewRepository.delete(review);
        reviewRepository.flush();
        updateReviewStatistics(book);
    }

    private void updateReviewStatistics(Book book) {
        ReviewStatistics statistics = reviewRepository.findStatisticsByBookId(book.getBookId());
        BigDecimal averageRating =
                BigDecimal.valueOf(statistics.getAverageRating()).setScale(2, RoundingMode.HALF_UP);
        book.updateReviewStatistics(averageRating, Math.toIntExact(statistics.getReviewCount()));
    }
}
