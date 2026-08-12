package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.AdminBookCreateRequest;
import com.bookeatinglion.book.dto.AdminBookResponse;
import com.bookeatinglion.book.dto.AdminBookUpdateRequest;
import com.bookeatinglion.book.service.AdminBookService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/admin/books")
@RequiredArgsConstructor
public class AdminBookController {

    private final AdminBookService adminBookService;

    @GetMapping
    public ApiResponse<Page<AdminBookResponse>> getBooks(
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminBookService.getBooks(includeDeleted, pageable));
    }

    @GetMapping("/{bookId}")
    public ApiResponse<AdminBookResponse> getBook(@PathVariable Long bookId) {
        return ApiResponse.success(adminBookService.getBook(bookId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminBookResponse> create(@Valid @RequestBody AdminBookCreateRequest request) {
        return ApiResponse.success(adminBookService.create(request));
    }

    @PatchMapping("/{bookId}")
    public ApiResponse<AdminBookResponse> update(
            @PathVariable Long bookId,
            @Valid @RequestBody AdminBookUpdateRequest request) {
        return ApiResponse.success(adminBookService.update(bookId, request));
    }

    @DeleteMapping("/{bookId}")
    public ApiResponse<Void> delete(@PathVariable Long bookId) {
        adminBookService.delete(bookId);
        return ApiResponse.success(null);
    }
}
