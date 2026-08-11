package com.bookeatinglion.order.cart.exception;

/** CartItem 도메인 불변식 위반(수량 1 미만). CartExceptionHandler 가 400 으로 매핑한다. */
public class InvalidCartQuantityException extends CartDomainException {

    public InvalidCartQuantityException(int quantity) {
        super(CartErrorCode.INVALID_REQUEST, "수량은 1 이상이어야 합니다: " + quantity);
    }
}
