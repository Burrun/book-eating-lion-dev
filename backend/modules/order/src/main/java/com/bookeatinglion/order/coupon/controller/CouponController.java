package com.bookeatinglion.order.coupon.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.order.coupon.dto.MemberCouponView;
import com.bookeatinglion.order.coupon.dto.RegisterCouponRequest;
import com.bookeatinglion.order.coupon.service.CouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/me")
    public ApiResponse<List<MemberCouponView>> getMyCoupons() {
        String memberId = SecurityUtils.currentMemberSub();
        return ApiResponse.success(couponService.getAvailableCoupons(memberId));
    }

    @PostMapping("/register")
    public ApiResponse<MemberCouponView> registerCoupon(@Valid @RequestBody RegisterCouponRequest request) {
        String memberId = SecurityUtils.currentMemberSub();
        return ApiResponse.success(couponService.registerCoupon(memberId, request.code()));
    }
}
