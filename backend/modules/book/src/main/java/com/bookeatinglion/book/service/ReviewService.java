package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.dto.ReviewRequest;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.exception.ReviewPermissionRequiredException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewPermissionRepository;
import com.bookeatinglion.book.repository.ReviewRepository;
import java.time.LocalDateTime;
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
        if (!bookRepository.existsById(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        return reviewRepository.findByBook_BookId(bookId, pageable).map(ReviewResponse::from);
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
    public ReviewResponse createReview(Long bookId, Long memberId, ReviewRequest request) {
        Book book = bookRepository.findById(bookId)
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
        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public void deleteReview(Long reviewId, Long memberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        if (!review.getMemberId().equals(memberId)) {
            throw new ReviewAccessDeniedException(reviewId, memberId);
        }
        reviewRepository.delete(review);
    }
}
