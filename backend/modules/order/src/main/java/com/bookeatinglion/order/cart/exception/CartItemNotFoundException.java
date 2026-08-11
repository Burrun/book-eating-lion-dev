package com.bookeatinglion.order.cart.exception;

public class CartItemNotFoundException extends CartDomainException {

    public CartItemNotFoundException(Long cartItemId) {
        super(CartErrorCode.CART_ITEM_NOT_FOUND, "장바구니 항목을 찾을 수 없습니다: " + cartItemId);
    }
}
