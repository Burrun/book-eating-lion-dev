package com.bookeatinglion.common.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** order-service가 발행하고 catalog-service가 소비하는 재입고 이벤트 계약. */
public record InventoryRestockedEvent(
        String eventId, Long bookId, int previousStock, int currentStock, String occurredAt) {

    public static final String STREAM_KEY = "events:inventory-restocked";

    public static InventoryRestockedEvent occurred(Long bookId, int previousStock, int currentStock) {
        return new InventoryRestockedEvent(
                UUID.randomUUID().toString(),
                bookId,
                previousStock,
                currentStock,
                LocalDateTime.now().toString());
    }

    public Map<String, String> toMap() {
        return Map.of(
                "eventId", eventId,
                "bookId", String.valueOf(bookId),
                "previousStock", String.valueOf(previousStock),
                "currentStock", String.valueOf(currentStock),
                "occurredAt", occurredAt);
    }

    public static InventoryRestockedEvent fromMap(Map<String, String> map) {
        return new InventoryRestockedEvent(
                map.get("eventId"),
                Long.valueOf(map.get("bookId")),
                Integer.parseInt(map.get("previousStock")),
                Integer.parseInt(map.get("currentStock")),
                map.get("occurredAt"));
    }
}
