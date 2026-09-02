package com.bookeatinglion.ai.wiki.controller;

import com.bookeatinglion.ai.wiki.service.AskMode;
import com.bookeatinglion.ai.wiki.service.DailyQuota;
import com.bookeatinglion.ai.wiki.service.WikiRagService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/ai/ask → ai-rag Deployment.
 *
 * <p>
 * 고객상담({@code /api/ai/bot/**})과 합치지 않는다. 자료도 목적도 다르다 — 이쪽은
 * 먹인 책 본문(S3 Vectors)이고 저쪽은 FAQ(ai_db)다.
 */
@RestController
@RequestMapping("/api/ai/lion")
@RequiredArgsConstructor
public class AskController {

    /** 계약 기본값. 서버는 이보다 많이 검색한 뒤 책당 상한을 적용하고 여기까지 자른다. */
    private static final int DEFAULT_TOP_K = 5;

    private final WikiRagService wikiRagService;
    private final DailyQuota dailyQuota;

    public record AskRequest(
            @NotBlank @Size(max = 500) String query, AskMode mode, List<Long> bookIds, @Min(1) @Max(10) Integer topK) {}

    /**
     * 쿼터는 <b>먼저 확인하고 나중에 소모한다.</b> 진입 즉시 소모하면 근거를 못 찾은 질의나
     * 서버 오류로 실패한 질의까지 사용자 한도를 깎는다(2026-08-31 실제로 겪음 —
     * {@code DailyQuota#consume} 주석 참고).
     */
    @PostMapping("/ask")
    public ApiResponse<WikiRagService.AskResult> ask(@Valid @RequestBody AskRequest request) {
        String memberId = SecurityUtils.currentMemberSub();
        dailyQuota.check(memberId);

        WikiRagService.AskResult result = wikiRagService.ask(
                memberId,
                request.query(),
                request.mode(),
                request.bookIds(),
                request.topK() == null ? DEFAULT_TOP_K : request.topK());

        // grounded=true 면 검색이 근거를 찾아 답을 돌려준 것이다 — SEARCH 모드처럼 LLM 을
        // 부르지 않았어도 질의 임베딩과 벡터 검색은 이미 과금됐으므로 센다(DailyQuota#consume).
        if (result.grounded()) {
            dailyQuota.consume(memberId);
        }
        return ApiResponse.success(result);
    }
}
