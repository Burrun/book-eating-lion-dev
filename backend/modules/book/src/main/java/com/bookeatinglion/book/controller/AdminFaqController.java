package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.FaqCreateRequest;
import com.bookeatinglion.book.dto.FaqResponse;
import com.bookeatinglion.book.dto.FaqUpdateRequest;
import com.bookeatinglion.book.service.FaqService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService faqService;

    @GetMapping
    public ApiResponse<List<FaqResponse>> getFaqs(@RequestParam(required = false) String category) {
        return ApiResponse.success(faqService.getAdminFaqs(category));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FaqResponse> create(@Valid @RequestBody FaqCreateRequest request) {
        return ApiResponse.success(faqService.create(request));
    }

    @PatchMapping("/{faqId}")
    public ApiResponse<FaqResponse> update(@PathVariable Long faqId, @Valid @RequestBody FaqUpdateRequest request) {
        return ApiResponse.success(faqService.update(faqId, request));
    }

    @DeleteMapping("/{faqId}")
    public ApiResponse<Void> delete(@PathVariable Long faqId) {
        faqService.delete(faqId);
        return ApiResponse.success(null);
    }
}
