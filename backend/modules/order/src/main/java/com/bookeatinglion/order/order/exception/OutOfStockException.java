package com.bookeatinglion.order.order.exception;

public class OutOfStockException extends OrderDomainException {

    public OutOfStockException(Long bookId) {
        super(OrderErrorCode.OUT_OF_STOCK, "재고가 부족합니다: bookId=" + bookId);
    }
}
