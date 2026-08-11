package com.bookeatinglion.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * catalog-service 의 공개 계약 GET /api/books/{bookId} 를 그대로 쓴다(인증 불필요 — 도서 조회는
 * 비로그인도 가능). order-service 의 첫 outbound 동기 호출이다(§7.6 예외).
 *
 * cart 와 order 가 공유한다 — 둘 다 "가격은 클라이언트를 신뢰하지 않고 catalog 에서 조회"가
 * 필요해서다. 단, degrade 응답을 다루는 방식은 갈린다: cart 는 화면 표시일 뿐이라 그대로
 * 보여줘도 되지만, order 는 그 가격으로 결제를 승인하면 안 된다 — BookDetailEnvelope.success()
 * 로 실제 응답인지 fallback degrade 인지 구분해서 order 쪽에서만 거부한다(CatalogClientFallback,
 * OrderService 참고).
 *
 * catalog-v1.yaml 에는 재고 벌크 조회 같은 bookIds 벌크 엔드포인트가 아직 없어 단건으로 N 회
 * 호출한다. catalog 쪽에 벌크 엔드포인트가 생기면 /internal/inventory 패턴으로 교체해야 한다.
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
