package com.bookeatinglion.usedbook.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.s3.dto.PresignedUrlRequest;
import com.bookeatinglion.s3.dto.PresignedUrlResponse;
import com.bookeatinglion.s3.service.S3PresignedUrlService;
import com.bookeatinglion.usedbook.domain.UsedBookStatus;
import com.bookeatinglion.usedbook.dto.UsedBookCreateRequest;
import com.bookeatinglion.usedbook.dto.UsedBookResponse;
import com.bookeatinglion.usedbook.dto.UsedBookSummaryResponse;
import com.bookeatinglion.usedbook.service.UsedBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/used-books")
@RequiredArgsConstructor
public class UsedBookController {

    private final UsedBookService usedBookService;
    private final S3PresignedUrlService s3PresignedUrlService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<UsedBookResponse> createUsedBook(@Valid @RequestBody UsedBookCreateRequest request) {
        return ApiResponse.success(usedBookService.createUsedBook(SecurityUtils.currentMemberSub(), request));
    }

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> getPresignedUrl(@Valid @RequestBody PresignedUrlRequest request) {
        return ApiResponse.success(s3PresignedUrlService.generatePresignedUrl(SecurityUtils.currentMemberSub(), request.fileName()));
    }

    @GetMapping
    public ApiResponse<Page<UsedBookSummaryResponse>> getUsedBooks(
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) UsedBookStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(usedBookService.getUsedBooks(isbn, status, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<UsedBookResponse> getUsedBook(@PathVariable Long id) {
        return ApiResponse.success(usedBookService.getUsedBook(id));
    }
}
