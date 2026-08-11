package com.bookeatinglion.order.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * order-service — 주문·결제·배송·재고·쿠폰·장바구니.
 *
 * 재고를 소유하므로 Redlock·재고차감·결제가 전부 이 프로세스 안의 로컬
 * 트랜잭션이다. §7.6 은 원래 "이 서비스에는 Feign 클라이언트가 하나도 없다"였으나
 * cart 가 유일한 예외다 — 도서 제목/가격/이미지는 catalog_db 에만 있어 order_db 만으로는
 * 장바구니 화면을 그릴 수 없다. CatalogClient 는 CatalogClientFallback 으로 degrade 되므로
 * catalog-service 장애가 장바구니를 막지 않는다(CartService 참고).
 */
@SpringBootApplication(scanBasePackages = {"com.bookeatinglion.order", "com.bookeatinglion.common"})
@EntityScan(basePackages = "com.bookeatinglion.order")
@EnableJpaRepositories(basePackages = "com.bookeatinglion.order")
@EnableFeignClients(basePackages = "com.bookeatinglion.order.cart.client")
public class OrderApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApiApplication.class, args);
    }
}
