package com.bookeatinglion.ai.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.bookeatinglion.ai.recommendation.dto.RecommendationRankRequest;
import com.bookeatinglion.ai.recommendation.port.RecommendationVectorPort;
import com.bookeatinglion.ai.recommendation.port.RecommendationVectorPort.Match;
import com.bookeatinglion.ai.wiki.service.GuardedAiCalls;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationRagServiceTest {

    @Mock
    private GuardedAiCalls ai;

    @Mock
    private RecommendationVectorPort vectorPort;

    @InjectMocks
    private RecommendationRagService service;

    @Test
    void 벡터_검색된_도서에_LLM_추천_이유를_결합한다() {
        when(ai.embed(anyString())).thenReturn(new float[] {1.0f, 0.0f});
        when(vectorPort.search(any(float[].class), eq(10)))
                .thenReturn(List.of(new Match(1L, "클린 코드", "로버트 마틴", "IT", 0.2)));
        when(ai.complete(anyString(), anyString())).thenReturn("1|최근 IT 도서에 관심을 보여 추천했어요.");

        var result = service.rank(new RecommendationRankRequest("member", "검색어: 리팩터링", 10));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().semanticScore()).isEqualTo(0.8);
        assertThat(result.getFirst().reason()).contains("IT 도서");
    }

    @Test
    void 외부_AI가_실패하면_빈_결과로_폴백한다() {
        when(ai.embed(anyString())).thenThrow(new IllegalStateException("bedrock unavailable"));

        assertThat(service.rank(new RecommendationRankRequest("member", "행동", 10)))
                .isEmpty();
    }
}
