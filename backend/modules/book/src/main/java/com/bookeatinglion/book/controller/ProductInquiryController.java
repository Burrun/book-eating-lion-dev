package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.InquiryCreateRequest;
import com.bookeatinglion.book.dto.InquiryResponse;
import com.bookeatinglion.book.dto.InquiryUpdateRequest;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ProductInquiryService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProductInquiryController {

    private final ProductInquiryService inquiryService;
    private final CatalogMemberIdentity memberIdentity;

    @GetMapping("/api/catalog/books/{bookId}/inquiries")
    public ApiResponse<Page<InquiryResponse>> getBookInquiries(
            @PathVariable Long bookId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                inquiryService.getBookInquiries(bookId, memberIdentity.optionalMemberId(), pageable));
    }

    @PostMapping("/api/catalog/books/{bookId}/inquiries")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InquiryResponse> create(
            @PathVariable Long bookId, @Valid @RequestBody InquiryCreateRequest request) {
        return ApiResponse.success(inquiryService.create(bookId, memberIdentity.requiredMemberId(), request));
    }

    @PatchMapping("/api/catalog/inquiries/{inquiryId}")
    public ApiResponse<InquiryResponse> update(
            @PathVariable Long inquiryId, @Valid @RequestBody InquiryUpdateRequest request) {
        return ApiResponse.success(inquiryService.update(inquiryId, memberIdentity.requiredMemberId(), request));
    }

    @DeleteMapping("/api/catalog/inquiries/{inquiryId}")
    public ApiResponse<Void> delete(@PathVariable Long inquiryId) {
        inquiryService.delete(inquiryId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }
}
