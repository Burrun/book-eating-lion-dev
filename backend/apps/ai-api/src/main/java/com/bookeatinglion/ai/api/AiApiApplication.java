package com.bookeatinglion.ai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * ai-service — 라이언/RAG, 문의봇, FAQ.
 *
 * 코드량은 가장 작지만 자원 특성이 병리적이라 분리했다 — "기능으로 3번 자르고,
 * 자원 프로파일로 한 번 더 잘랐다"의 그 한 번이다(판단 ①).
 *
 * 원래는 다른 서비스와 통신하지 않는다(스키마도, 커넥션 풀도, Pod 도, 스레드풀도 다르다).
 * 유일한 예외가 {@link com.bookeatinglion.ai.client.MemberSubscriptionClient}다 — 먹이기
 * EXP 2배 판정을 위해 member-service 구독 상태를 동기 조회하는데, 실패하면 1배로 안전
 * 강등될 뿐 먹이기 자체는 계속 성공한다(order-service의 CardClient와 동일 fail-safe 패턴).
 * 그래서 여기에 CPU 100% 부하를 걸어도 로그인·도서조회·결제가 멀쩡하다는 장애 격리
 * 시연은 여전히 성립한다(§7.6, Phase 4) — 반대 방향(member-service 장애)은 먹이기의
 * EXP 배율만 낮아질 뿐 RAG/채팅 등 이 서비스의 핵심 기능은 영향받지 않는다.
 */
@SpringBootApplication(scanBasePackages = {"com.bookeatinglion.ai", "com.bookeatinglion.common"})
@EnableFeignClients(basePackages = "com.bookeatinglion.ai.client")
@EntityScan(basePackages = "com.bookeatinglion.ai")
@EnableJpaRepositories(basePackages = "com.bookeatinglion.ai")
@ConfigurationPropertiesScan(
        basePackages = {
            "com.bookeatinglion.ai.api.config",
            "com.bookeatinglion.ai.wiki.config",
            "com.bookeatinglion.ai.bot.config"
        })
@org.springframework.scheduling.annotation.EnableScheduling
public class AiApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApiApplication.class, args);
    }
}
