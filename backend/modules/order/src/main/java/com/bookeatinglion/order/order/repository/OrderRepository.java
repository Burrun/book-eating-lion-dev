package com.bookeatinglion.order.order.repository;

import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByMemberId(String memberId, Pageable pageable);

    /** 관리자 주문 목록의 상태 필터용. */
    Page<Order> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);
}
