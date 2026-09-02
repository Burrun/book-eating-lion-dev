package com.bookeatinglion.member.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * member-service — 회원·인증·배송지·프리미엄 멤버십.
 *
 * 일부러 작고 지루하게 만든 서비스다. 인증은 화려할 이유가 없고 요구사항은 하나뿐이다
 * — 절대 안 죽는 것. 그래서 HPA 도 2→6 으로 가장 좁다.
 *
 * RAG 가 여기 들어오면 ①로그인이 CPU 를 굶고 ②HPA 가 엉뚱하게 뜨고
 * ③임베딩 로딩이 기동을 늘려 배포가 묶이고 ④OOM 시 전 서비스 인증이 끊긴다.
 */
@SpringBootApplication(scanBasePackages = {"com.bookeatinglion.member", "com.bookeatinglion.common"})
@EntityScan(basePackages = "com.bookeatinglion.member")
@EnableJpaRepositories(basePackages = "com.bookeatinglion.member")
public class MemberApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberApiApplication.class, args);
    }
}
