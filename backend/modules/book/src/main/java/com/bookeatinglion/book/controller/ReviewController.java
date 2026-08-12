package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.ReviewRequest;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.dto.ReviewUpdateRequest;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ReviewService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final CatalogMemberIdentity memberIdentity;

    @GetMapping("/api/catalog/books/{bookId}/reviews")
    public ApiResponse<Page<ReviewResponse>> getReviews(
            @PathVariable Long bookId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(reviewService.getReviews(bookId, pageable));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/catalog/books/{bookId}/reviews")
    public ApiResponse<ReviewResponse> createReview(
            @PathVariable Long bookId, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(reviewService.createReview(bookId, memberIdentity.requiredMemberId(), request));
    }

    @DeleteMapping("/api/catalog/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }

    @PatchMapping("/api/catalog/reviews/{reviewId}")
    public ApiResponse<ReviewResponse> updateReview(
            @PathVariable Long reviewId, @Valid @RequestBody ReviewUpdateRequest request) {
        return ApiResponse.success(reviewService.updateReview(reviewId, memberIdentity.requiredMemberId(), request));
    }
}
