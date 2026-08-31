package com.bookeatinglion.order.order.repository;

import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByMemberId(String memberId, Pageable pageable);

    /** 관리자 주문 목록의 상태 필터용. */
    Page<Order> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);

    /**
     * 카카오페이 승인(2단계)이 주문 행을 잠그고 읽는다 — 같은 주문에 승인 요청이 두 번
     * 겹쳐도 뒤엣놈이 앞엣놈의 커밋을 기다렸다가 {@code orderStatus != PENDING_PAYMENT} 를
     * 보고 카카오 승인 API 호출 전에 거절되게 한다. 이게 없으면 두 트랜잭션이 상태 검사를
     * 모두 통과한 뒤 하나가 {@code createDelivery} 의 UNIQUE 제약에 걸려 롤백되는데, 그
     * 시점엔 카카오 승인이 이미 끝나 있어 되돌릴 수 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(Long id);
}
