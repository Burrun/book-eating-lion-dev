package com.bookeatinglion.ai.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

/**
 * AWS SDK 클라이언트. 자격증명 공급자를 지정하지 않는다 — 기본 체인이 IRSA
 * (`AWS_WEB_IDENTITY_TOKEN_FILE`)를 알아서 집으므로 키를 주입할 이유가 없다.
 *
 * <p>타임아웃을 여기서 거는 게 핵심이다. Bedrock 호출은 1~3초가 정상이지만 상한이 없으면
 * Bulkhead 슬롯이 영원히 반환되지 않고, 그때는 슬롯이 20개든 200개든 결과가 같다.
 *
 * <p>스텁 프로필에서는 빈을 아예 만들지 않는다 — 로컬에 AWS 자격증명이 없어도 떠야 한다.
 */
@Configuration
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock")
public class AwsConfig {

    private static ClientOverrideConfiguration timeouts(AiProperties props) {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(props.bedrock().apiCallTimeout())
                .apiCallAttemptTimeout(props.bedrock().apiCallAttemptTimeout())
                .build();
    }

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(AiProperties props) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(props.bedrock().region()))
                .overrideConfiguration(timeouts(props))
                .build();
    }

    @Bean
    public S3VectorsClient s3VectorsClient(AiProperties props) {
        return S3VectorsClient.builder()
                .region(Region.of(props.bedrock().region()))
                .overrideConfiguration(timeouts(props))
                .build();
    }
}
