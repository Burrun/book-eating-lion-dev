package com.bookeatinglion.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * catalog-service 장애 시 cart/order 자체가 막히면 안 된다.
 *
 * success=false 로 정직하게 응답한다 — cart 는 이 플래그를 보지 않고 title/price 를
 * 그대로 화면에 degrade 시키지만(CartService), order 는 반드시 이 플래그를 확인해
 * price=0 짜리 주문이 승인되지 않도록 막는다(OrderService).
 */
@Slf4j
@Component
public class CatalogClientFallback implements CatalogClient {

    private static final String UNAVAILABLE_TITLE = "정보 조회 불가";

    @Override
    public BookDetailEnvelope getBook(Long bookId) {
        log.warn("catalog-service 도서 조회 실패 — degrade 합니다. bookId={}", bookId);
        return new BookDetailEnvelope(false, new BookView(bookId, UNAVAILABLE_TITLE, 0, null));
    }
}
