package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.BookService;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.SearchHistoryService;
import com.bookeatinglion.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final RecentViewedBookService recentViewedBookService;
    private final CatalogMemberIdentity memberIdentity;
    private final SearchHistoryService searchHistoryService;

    @GetMapping
    public ApiResponse<Page<BookSummaryResponse>> getBooks(
            @RequestParam(required = false) String category, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(bookService.getBooks(category, pageable));
    }

    @GetMapping("/search")
    public ApiResponse<Page<BookSummaryResponse>> search(
            @RequestParam String q, @PageableDefault(size = 20) Pageable pageable) {
        searchHistoryService.record(memberIdentity.optionalMemberId(), q);
        return ApiResponse.success(bookService.search(q, pageable));
    }

    @GetMapping("/bestsellers")
    public ApiResponse<List<BookSummaryResponse>> getBestsellers(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(bookService.getBestsellers(limit));
    }

    @GetMapping("/new-releases")
    public ApiResponse<List<BookSummaryResponse>> getNewReleases(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(bookService.getNewReleases(limit));
    }

    @GetMapping("/{bookId}")
    public ApiResponse<BookDetailResponse> getBook(@PathVariable Long bookId) {
        String memberId = memberIdentity.optionalMemberId();
        if (memberId != null) {
            recentViewedBookService.recordView(bookId, memberId);
        }
        return ApiResponse.success(bookService.getBook(bookId));
    }

    @GetMapping("/{bookId}/synopsis/detail")
    public ApiResponse<BookSynopsisDetailResponse> getSynopsisDetail(@PathVariable Long bookId) {
        return ApiResponse.success(bookService.getSynopsisDetail(bookId));
    }
}
