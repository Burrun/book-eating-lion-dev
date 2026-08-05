package com.bookeatinglion.order.repository;

import com.bookeatinglion.order.domain.Order;
import com.bookeatinglion.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);

    List<Order> findTop5ByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.orderStatus not in :excludedStatuses")
    long sumTotalAmountByOrderStatusNotIn(@Param("excludedStatuses") Collection<OrderStatus> excludedStatuses);
}
