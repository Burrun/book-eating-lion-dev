package com.bookeatinglion.order.inventory.repository;

import com.bookeatinglion.order.inventory.domain.Inventory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findByBookIdIn(Collection<Long> bookIds);
}
