package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.service.ReviewService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/admin/books")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{bookId}/reviews")
    public ApiResponse<Page<ReviewResponse>> getReviews(
            @PathVariable Long bookId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(reviewService.getAdminReviews(bookId, pageable));
    }
}
