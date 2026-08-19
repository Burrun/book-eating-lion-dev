package com.bookeatinglion.order.order.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.order.domain.OrderStatus;
import com.bookeatinglion.order.order.dto.AdminOrderSummaryResponse;
import com.bookeatinglion.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배송 상태 변경(PATCH)은 AdminDeliveryController(delivery.controller 패키지)에 있다 — 이 컨트롤러에
 * 합치면 DeliveryDomainException 이 이 패키지 전용인 OrderExceptionHandler 를 만나지 못해 처리되지
 * 않은 500 으로 샌다(RestControllerAdvice 는 basePackages 로 스코프된다). URL 프리픽스만 같다.
 */
@RestController
@RequestMapping("/api/orders/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<Page<AdminOrderSummaryResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(orderService.getAdminOrders(status, pageable));
    }
}
