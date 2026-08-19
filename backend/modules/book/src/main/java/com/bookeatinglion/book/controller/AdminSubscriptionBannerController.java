package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.SubscriptionBannerCreateRequest;
import com.bookeatinglion.book.dto.SubscriptionBannerResponse;
import com.bookeatinglion.book.dto.SubscriptionBannerUpdateRequest;
import com.bookeatinglion.book.service.SubscriptionBannerService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/admin/subscription-banners")
@RequiredArgsConstructor
public class AdminSubscriptionBannerController {

    private final SubscriptionBannerService subscriptionBannerService;

    @GetMapping
    public ApiResponse<List<SubscriptionBannerResponse>> getBanners() {
        return ApiResponse.success(subscriptionBannerService.getAdminBanners());
    }

    @GetMapping("/{bannerId}")
    public ApiResponse<SubscriptionBannerResponse> getBanner(@PathVariable Long bannerId) {
        return ApiResponse.success(subscriptionBannerService.getBanner(bannerId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriptionBannerResponse> create(
            @Valid @RequestBody SubscriptionBannerCreateRequest request) {
        return ApiResponse.success(subscriptionBannerService.create(request));
    }

    @PatchMapping("/{bannerId}")
    public ApiResponse<SubscriptionBannerResponse> update(
            @PathVariable Long bannerId, @Valid @RequestBody SubscriptionBannerUpdateRequest request) {
        return ApiResponse.success(subscriptionBannerService.update(bannerId, request));
    }

    @DeleteMapping("/{bannerId}")
    public ApiResponse<Void> delete(@PathVariable Long bannerId) {
        subscriptionBannerService.delete(bannerId);
        return ApiResponse.success(null);
    }
}
