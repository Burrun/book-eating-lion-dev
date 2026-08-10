package com.bookeatinglion.ai.lion.controller;

import com.bookeatinglion.ai.lion.domain.LionMemory;
import com.bookeatinglion.ai.lion.repository.LionRepository;
import com.bookeatinglion.ai.lion.service.RagService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/ai/lions/** → Ingress 가 ai-rag Deployment 로 라우팅한다.
 * 봇(/api/ai/bot/**)과 경로로 갈리는 이유는 두 워크로드의 자원 정책이 다르기 때문이다(판단 ④).
 */
@RestController
@RequestMapping("/api/ai/lions")
@RequiredArgsConstructor
public class LionController {

    private final RagService ragService;
    private final LionRepository lionRepository;

    public record AskRequest(@NotBlank String question) {}

    public record MemoryRequest(Long bookId, String bookTitle, String coverUrl, String memo, String quoteText) {}

    @PostMapping("/me/ask")
    public ApiResponse<String> ask(@Valid @RequestBody AskRequest request) {
        Long lionId = currentLionId();
        return ApiResponse.success(ragService.ask(lionId, request.question()));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/memories")
    public ApiResponse<Long> remember(@Valid @RequestBody MemoryRequest request) {
        Long lionId = currentLionId();

        // 도서 정보는 프론트가 도서 상세에서 진입하며 실어 보낸다. 통신 없음(§7.6).
        LionMemory saved = ragService.remember(new LionMemory(
                lionId, request.bookId(), request.bookTitle(), request.coverUrl(),
                request.memo(), request.quoteText()));

        return ApiResponse.success(saved.getLionMemoryId());
    }

    /** memberId 는 JWT 클레임에서 온다. member-service 호출 없음. */
    private Long currentLionId() {
        Long memberId = SecurityUtils.currentMemberId();
        return lionRepository
                .findByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("라이언이 없습니다: memberId=" + memberId))
                .getLionId();
    }
}
