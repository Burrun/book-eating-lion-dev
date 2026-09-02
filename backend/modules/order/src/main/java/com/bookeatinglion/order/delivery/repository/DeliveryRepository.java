package com.bookeatinglion.order.delivery.repository;

import com.bookeatinglion.order.delivery.domain.Delivery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    /** 관리자 주문 목록에서 배송상태를 N+1 없이 배치 조회하기 위한 용도. */
    List<Delivery> findByOrderIdIn(Collection<Long> orderIds);
}
