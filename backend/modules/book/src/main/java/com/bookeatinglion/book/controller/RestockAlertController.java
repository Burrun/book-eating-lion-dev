package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.domain.RestockAlertStatus;
import com.bookeatinglion.book.dto.RestockAlertResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.RestockAlertService;
import com.bookeatinglion.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RestockAlertController {
    private final RestockAlertService restockAlertService;
    private final CatalogMemberIdentity memberIdentity;

    @PostMapping("/api/catalog/books/{bookId}/restock-alert")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RestockAlertResponse> subscribe(@PathVariable Long bookId) {
        return ApiResponse.success(restockAlertService.subscribe(bookId, memberIdentity.requiredMemberId()));
    }

    @GetMapping("/api/catalog/restock-alerts/me")
    public ApiResponse<List<RestockAlertResponse>> getMyAlerts(
            @RequestParam(required = false) RestockAlertStatus status) {
        return ApiResponse.success(restockAlertService.getMyAlerts(memberIdentity.requiredMemberId(), status));
    }

    @DeleteMapping("/api/catalog/books/{bookId}/restock-alert")
    public ApiResponse<Void> cancel(@PathVariable Long bookId) {
        restockAlertService.cancel(bookId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }
}
