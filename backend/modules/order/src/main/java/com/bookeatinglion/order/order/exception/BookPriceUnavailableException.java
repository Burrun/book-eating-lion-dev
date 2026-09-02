package com.bookeatinglion.order.order.exception;

/** catalog-service 가 fallback 으로 degrade 되어 가격을 신뢰할 수 없을 때. price=0 주문 승인을 막는다. */
public class BookPriceUnavailableException extends OrderDomainException {

    public BookPriceUnavailableException(Long bookId) {
        super(OrderErrorCode.BOOK_PRICE_UNAVAILABLE, "도서 가격을 조회할 수 없습니다: bookId=" + bookId);
    }
}
