package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;
    private final CatalogMemberIdentity memberIdentity;

    @PostMapping("/{bookId}")
    public ApiResponse<Void> addWishlist(
            @PathVariable Long bookId) {
        wishlistService.addWishlist(bookId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{bookId}")
    public ApiResponse<Void> removeWishlist(
            @PathVariable Long bookId) {
        wishlistService.removeWishlist(bookId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }
}
