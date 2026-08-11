package com.bookeatinglion.order.cart.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * catalog-service 장애 시 장바구니 자체가 막히면 안 된다 — 결제로 가는 길목이다.
 * 항목별로 title/price 를 degrade 시키고 200 을 반환한다(요청 스펙).
 */
@Slf4j
@Component
public class CatalogClientFallback implements CatalogClient {

    private static final String UNAVAILABLE_TITLE = "정보 조회 불가";

    @Override
    public BookDetailEnvelope getBook(Long bookId) {
        log.warn("catalog-service 도서 조회 실패 — 장바구니 항목을 degrade 합니다. bookId={}", bookId);
        return new BookDetailEnvelope(true, new BookView(bookId, UNAVAILABLE_TITLE, 0, null));
    }
}
