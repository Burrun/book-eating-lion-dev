package com.bookeatinglion.order.inventory.service;

import com.bookeatinglion.common.event.InventoryRestockedEvent;
import com.bookeatinglion.order.event.InventoryRestockedPublisher;
import com.bookeatinglion.order.inventory.domain.Inventory;
import com.bookeatinglion.order.inventory.dto.InventoryView;
import com.bookeatinglion.order.inventory.repository.InventoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryRestockedPublisher inventoryRestockedPublisher;

    /**
     * 벌크 조회. catalog-service 가 도서 목록/상세를 그릴 때 호출한다.
     * 계약을 벌크로 못박은 이유는 단건 API 를 주면 목록에서 반드시 N+1 이 나기 때문이다.
     */
    public List<InventoryView> findByBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }
        return inventoryRepository.findByBookIdIn(bookIds).stream()
                .map(InventoryView::from)
                .toList();
    }

    /**
     * 관리자 입고. catalog-service 는 order_db 에 접근 권한이 없으므로
     * 도서 메타데이터만 자기 DB 에 쓰고 재고 반영은 이 API 로 위임한다(판단 ③ ①).
     * 저빈도·비실시간이라 동기 호출로 충분하고, 실패 시 관리자가 재시도하면 된다.
     */
    @Transactional
    public InventoryView restock(Long bookId, int quantity) {
        Inventory inventory = inventoryRepository
                .findById(bookId)
                // 신간 최초 입고면 이 시점에 재고 레코드가 생긴다.
                .orElseGet(() -> inventoryRepository.save(new Inventory(bookId, 0)));

        int previousStock = inventory.getStock();
        inventory.restock(quantity);
        if (previousStock == 0 && inventory.getStock() > 0) {
            inventoryRestockedPublisher.publish(
                    InventoryRestockedEvent.occurred(bookId, previousStock, inventory.getStock()));
        }
        return InventoryView.from(inventory);
    }
}
