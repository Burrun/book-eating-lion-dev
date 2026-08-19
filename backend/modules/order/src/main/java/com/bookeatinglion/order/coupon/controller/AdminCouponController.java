package com.bookeatinglion.order.coupon.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.coupon.dto.CouponCreateRequest;
import com.bookeatinglion.order.coupon.dto.CouponResponse;
import com.bookeatinglion.order.coupon.dto.CouponUpdateRequest;
import com.bookeatinglion.order.coupon.service.CouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons/admin")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping
    public ApiResponse<List<CouponResponse>> getCoupons() {
        return ApiResponse.success(couponService.getAllCoupons());
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponResponse> getCoupon(@PathVariable Long couponId) {
        return ApiResponse.success(couponService.getCoupon(couponId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponResponse> create(@Valid @RequestBody CouponCreateRequest request) {
        return ApiResponse.success(couponService.createCoupon(request));
    }

    @PatchMapping("/{couponId}")
    public ApiResponse<CouponResponse> update(
            @PathVariable Long couponId, @Valid @RequestBody CouponUpdateRequest request) {
        return ApiResponse.success(couponService.updateCoupon(couponId, request));
    }
}
