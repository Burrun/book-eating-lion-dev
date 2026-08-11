package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로가 /api/members/me/* 에서 /api/wishlist/me · /api/recent-books/me 로 바뀌었다.
 *
 * 이유는 Ingress 경로 라우팅이다(§7.5). /api/members/** 는 member-service 로 가는데,
 * 찜 목록과 최근 본 상품은 catalog_db 소유 데이터라 catalog-service 가 응답해야 한다.
 * 같은 접두사를 두 서비스가 나눠 가지면 라우팅 규칙이 경로 길이에 의존하게 되고,
 * 규칙 하나만 잘못 건드려도 요청이 엉뚱한 서비스로 간다.
 *
 * 프론트엔드 frontend/src/api/wishlist.ts 도 함께 수정했다.
 */
@RestController
@RequiredArgsConstructor
public class MemberBookQueryController {

    private final WishlistService wishlistService;
    private final RecentViewedBookService recentViewedBookService;

    @GetMapping("/api/wishlist/me")
    public ApiResponse<List<BookSummaryResponse>> getMyWishlist(@RequestHeader("X-Member-Id") Long memberId) {
        return ApiResponse.success(wishlistService.getMyWishlist(memberId));
    }

    @GetMapping("/api/recent-books/me")
    public ApiResponse<List<BookSummaryResponse>> getMyRecentBooks(
            @RequestHeader("X-Member-Id") Long memberId, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(recentViewedBookService.getMyRecentBooks(memberId, limit));
    }
}
