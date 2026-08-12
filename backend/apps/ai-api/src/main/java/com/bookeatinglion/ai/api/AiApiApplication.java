package com.bookeatinglion.ai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * ai-service — 라이언/RAG, 문의봇, FAQ.
 *
 * 코드량은 가장 작지만 자원 특성이 병리적이라 분리했다 — "기능으로 3번 자르고,
 * 자원 프로파일로 한 번 더 잘랐다"의 그 한 번이다(판단 ①).
 *
 * 이 서비스는 다른 서비스와 통신하지 않는다. 스키마도, 커넥션 풀도, Pod 도,
 * 스레드풀도 다르다. 그래서 여기에 CPU 100% 부하를 걸어도 로그인·도서조회·결제가
 * 멀쩡하다는 장애 격리 시연이 성립한다(§7.6, Phase 4).
 */
@SpringBootApplication(scanBasePackages = {"com.bookeatinglion.ai", "com.bookeatinglion.common"})
@EntityScan(basePackages = "com.bookeatinglion.ai")
@EnableJpaRepositories(basePackages = "com.bookeatinglion.ai")
@ConfigurationPropertiesScan(basePackages = {"com.bookeatinglion.ai.api.config", "com.bookeatinglion.ai.wiki.config"})
public class AiApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApiApplication.class, args);
    }
}
