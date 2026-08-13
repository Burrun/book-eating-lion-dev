package com.bookeatinglion.book.event;

import com.bookeatinglion.book.service.RestockAlertService;
import com.bookeatinglion.common.event.InventoryRestockedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryRestockedConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    private final RestockAlertService restockAlertService;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        restockAlertService.handleRestocked(InventoryRestockedEvent.fromMap(record.getValue()));
    }
}
