package com.bookeatinglion.order.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * order-service — 주문·결제·배송·재고·쿠폰.
 *
 * 재고를 소유하므로 Redlock·재고차감·결제가 전부 이 프로세스 안의 로컬
 * 트랜잭션이다. 이 서비스에는 Feign 클라이언트가 하나도 없다 — 나가는 동기
 * 호출이 없기 때문이다(§7.6).
 */
@SpringBootApplication(scanBasePackages = {"com.bookeatinglion.order", "com.bookeatinglion.common"})
@EntityScan(basePackages = "com.bookeatinglion.order")
@EnableJpaRepositories(basePackages = "com.bookeatinglion.order")
public class OrderApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApiApplication.class, args);
    }
}
