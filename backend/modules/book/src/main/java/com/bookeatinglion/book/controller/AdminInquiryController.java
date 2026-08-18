package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.domain.InquiryStatus;
import com.bookeatinglion.book.dto.InquiryAnswerRequest;
import com.bookeatinglion.book.dto.InquiryResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ProductInquiryService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final ProductInquiryService inquiryService;
    private final CatalogMemberIdentity memberIdentity;

    @GetMapping
    public ApiResponse<Page<InquiryResponse>> getInquiries(
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) InquiryStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(inquiryService.getAdminInquiries(bookId, status, pageable));
    }

    @PatchMapping("/{inquiryId}/answer")
    public ApiResponse<InquiryResponse> answer(
            @PathVariable Long inquiryId, @Valid @RequestBody InquiryAnswerRequest request) {
        return ApiResponse.success(inquiryService.answer(inquiryId, memberIdentity.requiredMemberId(), request));
    }
}
