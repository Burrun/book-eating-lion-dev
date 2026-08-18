package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.service.EbookService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EbookController {

    private final EbookService ebookService;

    @GetMapping("/api/catalog/books/{bookId}/ebook")
    public ApiResponse<EbookAccessResponse> getEbook(@PathVariable Long bookId) {
        return ApiResponse.success(ebookService.getAccess(bookId));
    }
}
