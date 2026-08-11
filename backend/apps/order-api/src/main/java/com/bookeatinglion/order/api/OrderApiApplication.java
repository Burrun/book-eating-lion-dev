package com.bookeatinglion.order.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * order-service — 주문·결제·배송·재고·쿠폰·장바구니.
 *
 * 재고를 소유하므로 Redlock·재고차감이 전부 이 프로세스 안의 로컬 트랜잭션이다. §7.6 은
 * 원래 "이 서비스에는 Feign 클라이언트가 하나도 없다"였으나 이제 outbound 클라이언트가
 * 둘이다 — CatalogClient(cart/order 공유, 도서 가격 조회)와 CardClient(order, 가상카드
 * 한도 차감/복구). 둘 다 fallback 으로 degrade 되지만 의미는 다르다: CatalogClient 는
 * cart 화면 degrade 는 허용해도 order 는 price=0 승인을 막고(OrderService), CardClient
 * 는 무응답을 곧장 결제 거절/취소 롤백으로 처리한다(PaymentService).
 */
@SpringBootApplication(scanBasePackages = {"com.bookeatinglion.order", "com.bookeatinglion.common"})
@EntityScan(basePackages = "com.bookeatinglion.order")
@EnableJpaRepositories(basePackages = "com.bookeatinglion.order")
@EnableFeignClients(basePackages = "com.bookeatinglion.order.client")
public class OrderApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApiApplication.class, args);
    }
}
