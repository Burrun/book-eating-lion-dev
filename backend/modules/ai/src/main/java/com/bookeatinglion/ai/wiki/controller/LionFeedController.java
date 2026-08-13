package com.bookeatinglion.ai.wiki.controller;

import com.bookeatinglion.ai.wiki.service.FeedService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/ai/lion/** → Ingress 가 ai-rag Deployment 로 라우팅한다. */
@RestController
@RequestMapping("/api/ai/lion")
@RequiredArgsConstructor
public class LionFeedController {

    private final FeedService feedService;

    public record FeedRequest(@NotNull Long bookId) {}

    @PostMapping("/feed")
    public ApiResponse<FeedService.LionStatus> feed(@Valid @RequestBody FeedRequest request) {
        // memberId 는 JWT 클레임에서 온다. member-service 호출 없음.
        return ApiResponse.success(feedService.feed(SecurityUtils.currentMemberSub(), request.bookId()));
    }

    @GetMapping("/me")
    public ApiResponse<FeedService.LionStatus> myLion() {
        return ApiResponse.success(feedService.getLionStatus(SecurityUtils.currentMemberSub()));
    }

    @GetMapping("/feedable-books")
    public ApiResponse<List<FeedService.FeedableBook>> feedableBooks() {
        return ApiResponse.success(feedService.feedableBooks(SecurityUtils.currentMemberSub()));
    }
}
