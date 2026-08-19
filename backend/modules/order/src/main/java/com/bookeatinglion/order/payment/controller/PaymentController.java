package com.bookeatinglion.order.payment.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.order.order.dto.OrderResponse;
import com.bookeatinglion.order.order.service.OrderService;
import com.bookeatinglion.order.payment.dto.KakaoApproveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카카오페이 2단계 흐름 중 approve 전용 — ready 는 /api/orders(POST) 안에서 일어난다. */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orderService;

    @PostMapping("/kakao/approve")
    public ApiResponse<OrderResponse> approveKakaoPayment(@Valid @RequestBody KakaoApproveRequest request) {
        String memberId = SecurityUtils.currentMemberSub();
        String nickname = SecurityUtils.currentNickname();
        return ApiResponse.success(
                orderService.approveKakaoPay(memberId, nickname, request.orderId(), request.pgToken()));
    }
}
