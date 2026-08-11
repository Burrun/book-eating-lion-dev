package com.bookeatinglion.order.cart.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * catalog-service 의 공개 계약 GET /api/books/{bookId} 를 그대로 쓴다(인증 불필요 — 도서 조회는
 * 비로그인도 가능). order-service 의 첫 outbound 동기 호출이다(§7.6 예외 — CartService 참고).
 *
 * catalog-v1.yaml 에는 재고 벌크 조회 같은 bookIds 벌크 엔드포인트가 아직 없어 단건으로 N 회
 * 호출한다. 장바구니 항목 수가 적어 지금은 영향이 작지만, catalog 쪽에 벌크 엔드포인트가 생기면
 * /internal/inventory 패턴으로 교체해야 한다.
 */
@FeignClient(name = "catalog-book", url = "${services.catalog.url}", fallback = CatalogClientFallback.class)
public interface CatalogClient {

    @GetMapping("/api/books/{bookId}")
    BookDetailEnvelope getBook(@PathVariable("bookId") Long bookId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookDetailEnvelope(boolean success, BookView data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookView(Long id, String title, int price, String coverImageUrl) {}
}
