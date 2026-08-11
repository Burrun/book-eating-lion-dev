package com.bookeatinglion.order.delivery.repository;

import com.bookeatinglion.order.delivery.domain.Delivery;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);
}
