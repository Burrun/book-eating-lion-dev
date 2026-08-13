package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.ReadingProgressRequest;
import com.bookeatinglion.book.dto.ReadingProgressResponse;
import com.bookeatinglion.book.service.ReadingProgressService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReadingProgressController {

    private final ReadingProgressService readingProgressService;

    @PutMapping("/api/books/{bookId}/reading-progress")
    public ApiResponse<ReadingProgressResponse> saveProgress(
            @PathVariable Long bookId,
            @Valid @RequestBody ReadingProgressRequest request) {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(readingProgressService.saveProgress(bookId, memberSub, request));
    }

    @GetMapping("/api/books/{bookId}/reading-progress")
    public ApiResponse<ReadingProgressResponse> getProgress(@PathVariable Long bookId) {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(readingProgressService.getProgress(bookId, memberSub));
    }
}
