package com.bookeatinglion.ai.wiki.controller;

import com.bookeatinglion.ai.wiki.service.FeedService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
     * "먹일 수 있는지" 판단(완독 여부)은 catalog-service 가 한다 — 이 컨트롤러는 그걸 확인하지
     * 않는다. memberId는 JWT 클레임에서만 온다(바디로 안 받는다 — 클라이언트가 다른 사람
     * memberId를 실어 보내는 경로를 원천 차단한다).
     *
     * <p>bookId 하나면 된다. 예전엔 메모 텍스트를 받아 벡터로 적재했지만, 이제 먹이기는
     * EXP 만 올리고 검색 인덱스는 건드리지 않는다.
     */
    public record FeedRequest(@NotNull Long bookId) {}

    @PostMapping("/feed")
    public ApiResponse<FeedService.LionStatus> feed(@Valid @RequestBody FeedRequest request) {
        return ApiResponse.success(feedService.feed(SecurityUtils.currentMemberSub(), request.bookId()));
    }

    @GetMapping("/me")
    public ApiResponse<FeedService.LionStatus> myLion() {
        return ApiResponse.success(feedService.getLionStatus(SecurityUtils.currentMemberSub()));
    }
}
