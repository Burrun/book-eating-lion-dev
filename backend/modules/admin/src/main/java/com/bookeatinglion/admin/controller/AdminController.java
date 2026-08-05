package com.bookeatinglion.admin.controller;

import com.bookeatinglion.admin.dto.AdminBookResponse;
import com.bookeatinglion.admin.dto.AdminMemberResponse;
import com.bookeatinglion.admin.dto.AdminOrderResponse;
import com.bookeatinglion.admin.dto.AuditLogResponse;
import com.bookeatinglion.admin.dto.DashboardStatsResponse;
import com.bookeatinglion.admin.service.AdminService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/books")
    public ApiResponse<Page<AdminBookResponse>> getBooks(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminService.getBooks(category, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders")
    public ApiResponse<Page<AdminOrderResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminService.getOrders(status, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/members")
    public ApiResponse<Page<AdminMemberResponse>> getMembers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminService.getMembers(pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard/stats")
    public ApiResponse<DashboardStatsResponse> getDashboardStats() {
        return ApiResponse.success(adminService.getDashboardStats());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLogResponse>> getAuditLogs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(adminService.getAuditLogs(pageable));
    }
}
