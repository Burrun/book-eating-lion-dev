package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.SubscriptionBannerResponse;
import com.bookeatinglion.book.service.SubscriptionBannerService;
import com.bookeatinglion.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/subscription-banners")
@RequiredArgsConstructor
public class SubscriptionBannerController {

    private final SubscriptionBannerService subscriptionBannerService;

    @GetMapping
    public ApiResponse<List<SubscriptionBannerResponse>> getBanners() {
        return ApiResponse.success(subscriptionBannerService.getCurrentlyActiveBanners());
    }
}
