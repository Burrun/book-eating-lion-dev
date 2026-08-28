package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookHighlightRequest;
import com.bookeatinglion.book.dto.BookHighlightResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.BookHighlightService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookHighlightController {

    private final BookHighlightService bookHighlightService;
    private final CatalogMemberIdentity memberIdentity;

    @PostMapping("/api/catalog/books/{bookId}/highlights")
    public ApiResponse<BookHighlightResponse> create(
            @PathVariable Long bookId, @Valid @RequestBody BookHighlightRequest request) {
        return ApiResponse.success(bookHighlightService.create(bookId, memberIdentity.requiredMemberId(), request));
    }

    @GetMapping("/api/catalog/members/me/highlights")
    public ApiResponse<List<BookHighlightResponse>> listMine() {
        return ApiResponse.success(bookHighlightService.listMine(memberIdentity.requiredMemberId()));
    }

    @DeleteMapping("/api/catalog/highlights/{highlightId}")
    public ApiResponse<Void> delete(@PathVariable Long highlightId) {
        bookHighlightService.delete(highlightId, memberIdentity.requiredMemberId());
        return ApiResponse.success(null);
    }
}
