package com.bookeatinglion.order.delivery.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.order.delivery.dto.DeliveryResponse;
import com.bookeatinglion.order.delivery.dto.UpdateDeliveryStatusRequest;
import com.bookeatinglion.order.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public ApiResponse<DeliveryResponse> getDelivery(@PathVariable Long orderId) {
        String memberId = SecurityUtils.currentMemberSub();
        return ApiResponse.success(deliveryService.getDeliveryByOrder(memberId, orderId));
    }

    @PatchMapping("/status")
    public ApiResponse<DeliveryResponse> updateStatus(
            @PathVariable Long orderId, @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        String memberId = SecurityUtils.currentMemberSub();
        return ApiResponse.success(deliveryService.updateDeliveryStatus(memberId, orderId, request.status()));
    }
}
