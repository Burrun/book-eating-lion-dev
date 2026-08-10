package com.bookeatinglion.catalog.api.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * order-service 의 /internal/inventory 계약. contracts/order-v1.yaml 과 1:1 이다.
 *
 * RestClient 가 아니라 OpenFeign 을 쓰는 이유는 fallback 속성 한 줄로
 * Phase 2-3 의 검증 기준("order-service 강제 종료 중에도 catalog 가 5xx 없이
 * fallback 응답")을 그대로 만족시키기 때문이다.
 *
 * 주소는 Eureka 없이 K8s Service DNS 가 해결한다(§4 — Service Discovery 미도입).
 */
@FeignClient(name = "order-inventory", url = "${services.order.url}", fallback = OrderInventoryFallback.class)
public interface OrderInventoryClient {

    /** 반드시 벌크. 도서 20건 렌더링에 호출 1회 (N+1 차단, 판단 ③). */
    @GetMapping("/internal/inventory")
    List<InventoryView> findByBookIds(@RequestParam("bookIds") List<Long> bookIds);

    /** 관리자 입고. catalog 는 order_db 에 쓰기 권한이 없으므로 이 API 로 위임한다. */
    @PostMapping("/internal/inventory/{bookId}/restock")
    InventoryView restock(@PathVariable("bookId") Long bookId, @RequestBody RestockRequest request);

    record InventoryView(Long bookId, int stock) {}

    record RestockRequest(int quantity) {}
}
