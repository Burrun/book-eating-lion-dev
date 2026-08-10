package com.bookeatinglion.catalog.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * catalog-service — 도서/리뷰/찜/스와이프/추천 추론.
 *
 * 스캔 범위를 com.bookeatinglion 전체가 아니라 자기 도메인으로 좁힌 것이 중요하다.
 * 넓게 잡으면 클래스패스에 우연히 올라온 남의 도메인이 함께 뜨면서 경계가 흐려진다.
 *
 * JPA Auditing 은 common 의 JpaConfig 가 켠다(여기서 또 켜면 빈 중복으로 기동 실패).
 */
@SpringBootApplication(scanBasePackages = {"com.bookeatinglion.catalog", "com.bookeatinglion.book", "com.bookeatinglion.common"})
@EntityScan(basePackages = "com.bookeatinglion.book")
@EnableJpaRepositories(basePackages = "com.bookeatinglion.book")
@EnableFeignClients(basePackages = "com.bookeatinglion.catalog.api.client")
public class CatalogApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApiApplication.class, args);
    }
}
