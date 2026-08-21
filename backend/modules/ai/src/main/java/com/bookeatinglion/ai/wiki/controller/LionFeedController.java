package com.bookeatinglion.ai.wiki.controller;

import com.bookeatinglion.ai.wiki.service.FeedService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /**
     * "먹일 수 있는지" 판단은 이제 catalog-service(완독 + 메모 작성)가 한다 — 이 컨트롤러는
     * 더 이상 그걸 확인하지 않는다. memberId는 JWT 클레임에서만 온다(바디로 안 받는다 —
     * 클라이언트가 다른 사람 memberId를 실어 보내는 경로를 원천 차단한다).
     */
    public record FeedRequest(
            @NotNull Long bookId, @NotBlank String bookTitle, @NotBlank @Size(max = 4000) String memoText) {}

    @PostMapping("/feed")
    public ApiResponse<FeedService.LionStatus> feed(@Valid @RequestBody FeedRequest request) {
        return ApiResponse.success(feedService.feed(
                SecurityUtils.currentMemberSub(), request.bookId(), request.bookTitle(), request.memoText()));
    }

    @GetMapping("/me")
    public ApiResponse<FeedService.LionStatus> myLion() {
        return ApiResponse.success(feedService.getLionStatus(SecurityUtils.currentMemberSub()));
    }
}
