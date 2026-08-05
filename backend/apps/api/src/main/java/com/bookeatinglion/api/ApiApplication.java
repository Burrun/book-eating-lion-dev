package com.bookeatinglion.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.bookeatinglion")
@EntityScan(basePackages = "com.bookeatinglion")
@EnableJpaRepositories(basePackages = "com.bookeatinglion")
// JPA Auditing 은 common 모듈의 JpaConfig 가 켠다 (여기서 또 켜면 jpaAuditingHandler 빈 중복으로 기동 실패)
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
