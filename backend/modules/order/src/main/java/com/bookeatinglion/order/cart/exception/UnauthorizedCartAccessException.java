package com.bookeatinglion.order.cart.exception;

public class UnauthorizedCartAccessException extends CartDomainException {

    public UnauthorizedCartAccessException(Long cartItemId) {
        super(CartErrorCode.UNAUTHORIZED_CART_ACCESS, "본인의 장바구니 항목이 아닙니다: " + cartItemId);
    }
}
