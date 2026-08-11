package com.bookeatinglion.order.order.repository;

import com.bookeatinglion.order.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
