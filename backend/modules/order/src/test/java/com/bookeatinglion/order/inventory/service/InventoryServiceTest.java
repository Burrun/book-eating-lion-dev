package com.bookeatinglion.order.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.event.InventoryRestockedPublisher;
import com.bookeatinglion.order.inventory.domain.Inventory;
import com.bookeatinglion.order.inventory.repository.InventoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock
    InventoryRepository inventoryRepository;

    @Mock
    InventoryRestockedPublisher publisher;

    @InjectMocks
    InventoryService inventoryService;

    @Test
    void 재고가_0에서_양수가_되면_재입고_이벤트를_발행한다() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(new Inventory(1L, 0)));

        var result = inventoryService.restock(1L, 5);

        assertThat(result.stock()).isEqualTo(5);
        verify(publisher).publish(any());
    }

    @Test
    void 이미_재고가_있으면_이벤트를_발행하지_않는다() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(new Inventory(1L, 5)));

        inventoryService.restock(1L, 5);

        verify(publisher, never()).publish(any());
    }
}
