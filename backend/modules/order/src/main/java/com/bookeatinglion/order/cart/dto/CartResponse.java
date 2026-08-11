package com.bookeatinglion.order.cart.dto;

import java.util.List;

public record CartResponse(List<CartItemView> items, int totalQuantity, long totalPrice) {

    public static CartResponse of(List<CartItemView> items) {
        int totalQuantity = items.stream().mapToInt(CartItemView::quantity).sum();
        long totalPrice = items.stream().mapToLong(CartItemView::subtotal).sum();
        return new CartResponse(items, totalQuantity, totalPrice);
    }
}
