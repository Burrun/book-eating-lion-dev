package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.RecommendationQueueResponse;
import com.bookeatinglion.book.dto.RecommendationReactionRequest;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.RecommendationService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/recommend/queue")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final CatalogMemberIdentity memberIdentity;

    @GetMapping
    public ApiResponse<RecommendationQueueResponse> getQueue(@RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(recommendationService.getQueue(memberIdentity.requiredMemberId(), refresh));
    }

    @PostMapping("/reaction")
    public ApiResponse<Void> react(@Valid @RequestBody RecommendationReactionRequest request) {
        recommendationService.react(memberIdentity.requiredMemberId(), request);
        return ApiResponse.success(null);
    }
}
