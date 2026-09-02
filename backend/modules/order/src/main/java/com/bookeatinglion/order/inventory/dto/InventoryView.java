package com.bookeatinglion.order.inventory.dto;

import com.bookeatinglion.order.inventory.domain.Inventory;

/** /internal/inventory 응답 요소. contracts/order-v1.yaml 의 InventoryView 와 1:1 이다. */
public record InventoryView(Long bookId, int stock) {

    public static InventoryView from(Inventory inventory) {
        return new InventoryView(inventory.getBookId(), inventory.getStock());
    }
}
