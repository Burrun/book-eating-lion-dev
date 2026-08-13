package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.ReadingProgressRequest;
import com.bookeatinglion.book.dto.ReadingProgressResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ReadingProgressService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReadingProgressController {

    private final ReadingProgressService readingProgressService;
    private final CatalogMemberIdentity memberIdentity;

    @PutMapping("/api/catalog/books/{bookId}/reading-progress")
    public ApiResponse<ReadingProgressResponse> saveProgress(
            @PathVariable Long bookId,
            @Valid @RequestBody ReadingProgressRequest request) {
        return ApiResponse.success(
                readingProgressService.saveProgress(bookId, memberIdentity.requiredMemberId(), request));
    }

    @GetMapping("/api/catalog/books/{bookId}/reading-progress")
    public ApiResponse<ReadingProgressResponse> getProgress(@PathVariable Long bookId) {
        return ApiResponse.success(readingProgressService.getProgress(bookId, memberIdentity.requiredMemberId()));
    }
}
