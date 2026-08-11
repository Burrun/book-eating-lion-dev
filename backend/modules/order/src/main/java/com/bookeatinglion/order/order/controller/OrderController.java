package com.bookeatinglion.order.order.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.order.order.dto.CreateOrderRequest;
import com.bookeatinglion.order.order.dto.OrderResponse;
import com.bookeatinglion.order.order.dto.RequestReturnRequest;
import com.bookeatinglion.order.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(orderService.createOrder(memberId, request));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(orderService.getOrder(memberId, orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long orderId) {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(orderService.cancelOrder(memberId, orderId));
    }

    @PostMapping("/{orderId}/return")
    public ApiResponse<OrderResponse> requestReturn(
            @PathVariable Long orderId, @Valid @RequestBody RequestReturnRequest request) {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(orderService.requestReturn(memberId, orderId, request.reason()));
    }

    @PostMapping("/{orderId}/refund")
    public ApiResponse<OrderResponse> refundOrder(@PathVariable Long orderId) {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(orderService.refundOrder(memberId, orderId));
    }
}
