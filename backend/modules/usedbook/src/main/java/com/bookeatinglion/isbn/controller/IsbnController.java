package com.bookeatinglion.isbn.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.isbn.dto.IsbnLookupResponse;
import com.bookeatinglion.isbn.service.IsbnLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/isbn")
@RequiredArgsConstructor
public class IsbnController {

    private final IsbnLookupService isbnLookupService;

    @GetMapping("/{isbn}/lookup")
    public ApiResponse<IsbnLookupResponse> lookup(@PathVariable String isbn) {
        return ApiResponse.success(isbnLookupService.lookup(isbn));
    }
}
