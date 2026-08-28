package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.FeedableBookResponse;
import com.bookeatinglion.book.dto.ReadingProgressRequest;
import com.bookeatinglion.book.dto.ReadingProgressResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ReadingProgressService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReadingProgressController {

    private final ReadingProgressService readingProgressService;
    private final CatalogMemberIdentity memberIdentity;

    @PutMapping("/api/catalog/books/{bookId}/reading-progress")
    public ApiResponse<ReadingProgressResponse> saveProgress(
            @PathVariable Long bookId, @Valid @RequestBody ReadingProgressRequest request) {
        return ApiResponse.success(
                readingProgressService.saveProgress(bookId, memberIdentity.requiredMemberId(), request));
    }

    @GetMapping("/api/catalog/books/{bookId}/reading-progress")
    public ApiResponse<ReadingProgressResponse> getProgress(@PathVariable Long bookId) {
        return ApiResponse.success(readingProgressService.getProgress(bookId, memberIdentity.requiredMemberId()));
    }

    /** 마이페이지가 사자에게 먹일 카드로 그린다 — 완독했고 아직 안 먹인 책. */
    @GetMapping("/api/catalog/members/me/books/feedable")
    public ApiResponse<List<FeedableBookResponse>> feedableBooks() {
        return ApiResponse.success(readingProgressService.listFeedableBooks(memberIdentity.requiredMemberId()));
    }

    /** POST /api/ai/lion/feed 성공 직후 프론트가 부른다. */
    @PatchMapping("/api/catalog/books/{bookId}/reading-progress/fed")
    public ApiResponse<Void> markFed(@PathVariable Long bookId) {
        readingProgressService.markFed(bookId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }
}
