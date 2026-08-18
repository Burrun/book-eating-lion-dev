package com.bookeatinglion.order.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * 구매 확정 이벤트 발행용 SQS 클라이언트. 자격증명 공급자를 지정하지 않는다 — 기본 체인이
 * IRSA(AWS_WEB_IDENTITY_TOKEN_FILE)를 알아서 집으므로 키를 주입할 이유가 없다.
 */
@Configuration
public class AwsConfig {

    @Bean
    public SqsClient sqsClient(@Value("${sqs.region}") String region) {
        return SqsClient.builder().region(Region.of(region)).build();
    }
}
