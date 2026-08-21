package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookMemoRequest;
import com.bookeatinglion.book.dto.BookMemoResponse;
import com.bookeatinglion.book.dto.FedMemoResponse;
import com.bookeatinglion.book.dto.FeedableMemoResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.BookMemoService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookMemoController {

    private final BookMemoService bookMemoService;
    private final CatalogMemberIdentity memberIdentity;

    @PutMapping("/api/catalog/books/{bookId}/memo")
    public ApiResponse<BookMemoResponse> saveMemo(
            @PathVariable Long bookId, @Valid @RequestBody BookMemoRequest request) {
        return ApiResponse.success(
                bookMemoService.upsertMemo(bookId, memberIdentity.requiredMemberId(), request.memoText()));
    }

    @GetMapping("/api/catalog/books/{bookId}/memo")
    public ApiResponse<BookMemoResponse> getMemo(@PathVariable Long bookId) {
        return ApiResponse.success(bookMemoService.getMemo(bookId, memberIdentity.requiredMemberId()));
    }

    @GetMapping("/api/catalog/members/me/memos/feedable")
    public ApiResponse<List<FeedableMemoResponse>> feedableMemos() {
        return ApiResponse.success(bookMemoService.listFeedableMemos(memberIdentity.requiredMemberId()));
    }

    @GetMapping("/api/catalog/members/me/memos/fed")
    public ApiResponse<List<FedMemoResponse>> fedMemos() {
        return ApiResponse.success(bookMemoService.listFedMemos(memberIdentity.requiredMemberId()));
    }

    @PatchMapping("/api/catalog/books/{bookId}/memo/fed")
    public ApiResponse<Void> markFed(@PathVariable Long bookId) {
        bookMemoService.markFed(bookId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }
}
