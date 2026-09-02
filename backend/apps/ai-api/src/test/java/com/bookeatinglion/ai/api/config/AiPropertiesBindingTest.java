package com.bookeatinglion.ai.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.ai.wiki.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * application.yml 의 환경변수 플레이스홀더가 실제로 풀리는지 본다.
 *
 * <p>이걸 테스트하는 이유는 실패 방식 때문이다. 기본값이 안 풀리면 파드는 뜨는데 모델 ID 가
 * 리터럴 {@code "${AI_LLM_MODEL:...}"} 이 되어 첫 Bedrock 호출에서야 죽고, 거리 임계값이
 * 안 풀리면 바인딩 단계에서 죽는다 — 어느 쪽이든 배포 후에 발견된다.
 */
class AiPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties({AiProperties.class, RagProperties.class})
    static class Props {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Props.class);

    /** 기본값 안에 콜론이 들어 있다({@code ...-v1:0}). 첫 콜론만 구분자로 먹혀야 한다. */
    @Test
    void 환경변수가_없으면_llm_모델_기본값이_풀린다() {
        runner.run(context -> assertThat(
                        context.getBean(AiProperties.class).bedrock().llmModel())
                .isEqualTo("global.anthropic.claude-haiku-4-5-20251001-v1:0"));
    }

    /**
     * Bedrock 의 최신 Claude 모델은 inferenceTypesSupported 가 [INFERENCE_PROFILE] 뿐이라
     * 맨 모델 ID 로는 호출이 거부된다. 기본값에서 접두사가 빠지면 첫 Bedrock 호출에서야 터진다.
     */
    @Test
    void llm_모델_기본값은_추론_프로파일_ID_다() {
        runner.run(context -> assertThat(
                        context.getBean(AiProperties.class).bedrock().llmModel())
                .startsWith("global."));
    }

    @Test
    void 환경변수가_있으면_llm_모델을_덮어쓴다() {
        runner.withPropertyValues("AI_LLM_MODEL=us.anthropic.claude-haiku-4-5-20251001-v1:0")
                .run(context -> assertThat(
                                context.getBean(AiProperties.class).bedrock().llmModel())
                        .isEqualTo("us.anthropic.claude-haiku-4-5-20251001-v1:0"));
    }

    /** 문자열 플레이스홀더가 double/int 로 바인딩되는지 — 안 되면 기동 자체가 실패한다. */
    @Test
    void 거리_임계값과_쿼터가_숫자로_바인딩된다() {
        runner.run(context -> {
            RagProperties rag = context.getBean(RagProperties.class);
            assertThat(rag.maxDistance()).isEqualTo(0.75);
            assertThat(rag.freeDailyQuota()).isEqualTo(5);
            assertThat(rag.subscribedDailyQuota()).isEqualTo(50);
        });
    }

    @Test
    void 환경변수로_거리_임계값과_쿼터를_흔들_수_있다() {
        runner.withPropertyValues("AI_MAX_DISTANCE=0.42", "AI_DAILY_QUOTA_FREE=1", "AI_DAILY_QUOTA_SUBSCRIBED=999")
                .run(context -> {
                    RagProperties rag = context.getBean(RagProperties.class);
                    assertThat(rag.maxDistance()).isEqualTo(0.42);
                    assertThat(rag.freeDailyQuota()).isEqualTo(1);
                    assertThat(rag.subscribedDailyQuota()).isEqualTo(999);
                });
    }

    /** 인덱스와 짝인 값은 환경변수가 아니다. 실수로 빼면 이 테스트가 막는다. */
    @Test
    void 임베딩_모델과_차원은_환경변수로_바뀌지_않는다() {
        runner.withPropertyValues("AI_EMBEDDING_MODEL=amazon.titan-embed-text-v1", "AI_EMBEDDING_DIMENSION=1536")
                .run(context -> {
                    AiProperties.Bedrock bedrock =
                            context.getBean(AiProperties.class).bedrock();
                    assertThat(bedrock.embeddingModel()).isEqualTo("amazon.titan-embed-text-v2:0");
                    assertThat(bedrock.embeddingDimension()).isEqualTo(1024);
                });
    }
}
