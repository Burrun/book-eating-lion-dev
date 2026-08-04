package com.bookeatinglion.delivery.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.delivery.dto.DeliveryResponse;
import com.bookeatinglion.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public ApiResponse<DeliveryResponse> getDelivery(@PathVariable Long orderId) {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(deliveryService.getDeliveryByOrder(memberSub, orderId));
    }
}
