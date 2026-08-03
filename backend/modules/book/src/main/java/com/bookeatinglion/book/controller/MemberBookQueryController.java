package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberBookQueryController {

    private final WishlistService wishlistService;
    private final RecentViewedBookService recentViewedBookService;

    @GetMapping("/wishlist")
    public ApiResponse<List<BookSummaryResponse>> getMyWishlist(
            @RequestHeader("X-Member-Id") Long memberId) {
        return ApiResponse.success(wishlistService.getMyWishlist(memberId));
    }

    @GetMapping("/recent-books")
    public ApiResponse<List<BookSummaryResponse>> getMyRecentBooks(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(recentViewedBookService.getMyRecentBooks(memberId, limit));
    }
}
