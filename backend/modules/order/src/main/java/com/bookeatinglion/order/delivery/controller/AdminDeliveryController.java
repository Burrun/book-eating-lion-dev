package com.bookeatinglion.order.delivery.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.delivery.dto.DeliveryResponse;
import com.bookeatinglion.order.delivery.dto.UpdateDeliveryStatusRequest;
import com.bookeatinglion.order.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 주문 목록 조회(GET)는 AdminOrderController(order.controller 패키지)에 있다 — 이유는 그쪽 주석 참고. */
@RestController
@RequestMapping("/api/orders/admin")
@RequiredArgsConstructor
public class AdminDeliveryController {

    private final DeliveryService deliveryService;

    @PatchMapping("/{orderId}/delivery-status")
    public ApiResponse<DeliveryResponse> updateDeliveryStatus(
            @PathVariable Long orderId, @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return ApiResponse.success(deliveryService.updateDeliveryStatusAsAdmin(orderId, request.status()));
    }
}
