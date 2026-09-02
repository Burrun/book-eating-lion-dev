package com.bookeatinglion.catalog.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * 신간 등록 이벤트 발행용 SQS 클라이언트. 자격증명 공급자를 지정하지 않는다 — 기본 체인이
 * IRSA(AWS_WEB_IDENTITY_TOKEN_FILE)를 알아서 집으므로 키를 주입할 이유가 없다
 * (order-api의 AwsConfig와 같은 컨벤션).
 */
@Configuration
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(@Value("${aws.region:ap-northeast-2}") String region) {
        return SqsClient.builder().region(Region.of(region)).build();
    }
}
