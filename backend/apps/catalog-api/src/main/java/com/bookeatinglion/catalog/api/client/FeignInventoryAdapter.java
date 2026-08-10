package com.bookeatinglion.catalog.api.client;

import com.bookeatinglion.book.port.InventoryPort;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * modules/book 이 선언한 포트를 Feign 으로 구현한다.
 *
 * 이 어댑터가 app 레이어에 있는 덕분에 modules/book 은 Feign 도, order-service 의
 * 존재도 모른다. 나중에 통신 수단을 바꿔도 도메인 코드는 그대로다(§7.1).
 */
@Component
@RequiredArgsConstructor
public class FeignInventoryAdapter implements InventoryPort {

    private final OrderInventoryClient orderInventoryClient;

    @Override
    public Map<Long, Integer> stockByBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }
        return orderInventoryClient.findByBookIds(bookIds).stream()
                .collect(Collectors.toMap(
                        OrderInventoryClient.InventoryView::bookId,
                        OrderInventoryClient.InventoryView::stock,
                        (a, b) -> a));
    }
}
