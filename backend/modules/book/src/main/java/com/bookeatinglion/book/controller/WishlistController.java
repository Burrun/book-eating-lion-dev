package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{bookId}")
    public ApiResponse<Void> addWishlist(
            @PathVariable Long bookId,
            @RequestHeader("X-Member-Id") Long memberId) {
        wishlistService.addWishlist(bookId, memberId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{bookId}")
    public ApiResponse<Void> removeWishlist(
            @PathVariable Long bookId,
            @RequestHeader("X-Member-Id") Long memberId) {
        wishlistService.removeWishlist(bookId, memberId);
        return ApiResponse.success(null);
    }
}
