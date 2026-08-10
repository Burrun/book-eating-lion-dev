package com.bookeatinglion.order.inventory.domain;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long bookId, int available, int requested) {
        super("재고 부족: bookId=" + bookId + ", available=" + available + ", requested=" + requested);
    }
}
