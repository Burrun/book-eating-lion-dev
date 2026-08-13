package com.bookeatinglion.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventoryRestockedEventTest {
    @Test
    void redis_stream_map으로_왕복_변환한다() {
        var event = InventoryRestockedEvent.occurred(10L, 0, 20);

        var restored = InventoryRestockedEvent.fromMap(event.toMap());

        assertThat(restored).isEqualTo(event);
    }
}
