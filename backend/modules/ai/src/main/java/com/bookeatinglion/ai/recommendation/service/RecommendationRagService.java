package com.bookeatinglion.ai.recommendation.service;

import com.bookeatinglion.ai.recommendation.dto.RankedRecommendation;
import com.bookeatinglion.ai.recommendation.dto.RecommendationRankRequest;
import com.bookeatinglion.ai.recommendation.port.RecommendationVectorPort;
import com.bookeatinglion.ai.recommendation.port.RecommendationVectorPort.Match;
import com.bookeatinglion.ai.wiki.service.GuardedAiCalls;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 사용자 행동 근거를 검색 질의로 사용하고, 검색된 도서만 LLM에 전달하는 추천 RAG 경로다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationRagService {

    private final GuardedAiCalls ai;
    private final RecommendationVectorPort vectorPort;

    public List<RankedRecommendation> rank(RecommendationRankRequest request) {
        try {
            float[] preferenceVector = ai.embed(request.preferenceEvidence());
            List<Match> matches = vectorPort.search(preferenceVector, request.topK());
            return groundedResults(request, matches);
        } catch (RuntimeException e) {
            log.warn("추천 임베딩·벡터 검색·이유 생성 실패 — Catalog 규칙 점수로 폴백합니다. memberId={}", request.memberId(), e);
            return List.of();
        }
    }

    private List<RankedRecommendation> groundedResults(RecommendationRankRequest request, List<Match> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }

        String generated = ai.complete(systemPrompt(request.preferenceEvidence(), matches), "추천 이유를 생성해 주세요.");
        Map<Long, String> reasons = parseReasons(generated);
        return matches.stream()
                .map(match -> {
                    String reason =
                            reasons.getOrDefault(match.bookId(), "%s 분야에 보인 관심과 유사한 도서예요.".formatted(match.category()));
                    return new RankedRecommendation(match.bookId(), similarity(match.distance()), reason);
                })
                .toList();
    }

    private static String systemPrompt(String evidence, List<Match> matches) {
        StringBuilder prompt = new StringBuilder(
                """
                당신은 도서 추천 이유 작성기입니다.
                아래 사용자 행동 근거와 벡터 검색 결과에 명시된 정보만 사용하세요.
                구매·조회·찜 등 제공되지 않은 행동을 만들지 마세요.
                각 도서마다 한국어 한 문장으로 작성하고 반드시 `bookId|이유` 형식을 한 줄씩 사용하세요.

                [사용자 행동 근거]
                """);
        prompt.append(evidence).append("\n\n[벡터 검색 결과]\n");
        for (Match match : matches) {
            prompt.append("%d | %s | %s | %s | 거리 %.4f%n"
                    .formatted(match.bookId(), match.title(), match.author(), match.category(), match.distance()));
        }
        return prompt.toString();
    }

    private static Map<Long, String> parseReasons(String generated) {
        Map<Long, String> reasons = new HashMap<>();
        if (generated == null) {
            return reasons;
        }
        for (String line : generated.lines().toList()) {
            int separator = line.indexOf('|');
            if (separator < 1) {
                continue;
            }
            try {
                long bookId = Long.parseLong(line.substring(0, separator).trim());
                String reason = line.substring(separator + 1).trim();
                if (!reason.isBlank()) {
                    reasons.put(bookId, reason);
                }
            } catch (NumberFormatException ignored) {
                // 형식이 아닌 설명 문장은 버리고 해당 도서에는 고정 근거 문구를 사용한다.
            }
        }
        return reasons;
    }

    private static double similarity(double distance) {
        return Math.max(0.0, Math.min(1.0, 1.0 - distance));
    }
}
