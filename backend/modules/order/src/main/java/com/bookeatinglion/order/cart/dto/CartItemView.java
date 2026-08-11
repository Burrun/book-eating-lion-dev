package com.bookeatinglion.order.cart.dto;

import com.bookeatinglion.order.cart.client.CatalogClient.BookView;
import com.bookeatinglion.order.cart.domain.CartItem;

public record CartItemView(
        Long cartItemId, Long bookId, String title, int price, String coverImageUrl, int quantity, long subtotal) {

    public static CartItemView of(CartItem cartItem, BookView book) {
        return new CartItemView(
                cartItem.getId(),
                cartItem.getBookId(),
                book.title(),
                book.price(),
                book.coverImageUrl(),
                cartItem.getQuantity(),
                (long) book.price() * cartItem.getQuantity());
    }
}
