package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.FaqResponse;
import com.bookeatinglion.book.service.FaqService;
import com.bookeatinglion.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/api/catalog/faqs")
    public ApiResponse<List<FaqResponse>> getFaqs(@RequestParam(required = false) String category) {
        return ApiResponse.success(faqService.getActiveFaqs(category));
    }
}
