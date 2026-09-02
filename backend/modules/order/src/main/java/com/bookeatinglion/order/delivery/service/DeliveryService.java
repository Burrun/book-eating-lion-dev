package com.bookeatinglion.order.delivery.service;

import com.bookeatinglion.order.delivery.domain.Delivery;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.delivery.dto.DeliveryResponse;
import com.bookeatinglion.order.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.order.delivery.exception.UnauthorizedDeliveryAccessException;
import com.bookeatinglion.order.delivery.repository.DeliveryRepository;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이 클래스가 MSA 전환에서 잘라야 했던 유일한 이음새였다(계획서 §1.2).
 *
 * 이전에는 MemberRepository 로 cognitoSub → Member 를 조회해 소유권을 검증했다.
 * member_db 는 이제 order-service 의 접근 권한 밖이므로 그 조회가 불가능하고,
 * 대신 JWT 클레임의 memberId 를 그대로 쓴다. 동기 호출을 추가한 게 아니라
 * 호출 자체가 사라진 것이다(판단 ③).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;

    public DeliveryResponse getDeliveryByOrder(String memberId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new DeliveryNotFoundException(orderId));

        if (!order.getMemberId().equals(memberId)) {
            throw new UnauthorizedDeliveryAccessException(orderId);
        }

        Delivery delivery =
                deliveryRepository.findByOrderId(orderId).orElseThrow(() -> new DeliveryNotFoundException(orderId));

        return DeliveryResponse.from(delivery);
    }

    @Transactional
    public DeliveryResponse updateDeliveryStatus(String memberId, Long orderId, DeliveryStatus newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new DeliveryNotFoundException(orderId));

        if (!order.getMemberId().equals(memberId)) {
            throw new UnauthorizedDeliveryAccessException(orderId);
        }

        Delivery delivery =
                deliveryRepository.findByOrderId(orderId).orElseThrow(() -> new DeliveryNotFoundException(orderId));
        delivery.updateStatus(newStatus);

        return DeliveryResponse.from(delivery);
    }

    /** 관리자용 배송 상태 변경. 소유권 검증은 하지 않는다 — 인가는 SecurityConfig(hasRole("ADMIN"))가 담당한다. */
    @Transactional
    public DeliveryResponse updateDeliveryStatusAsAdmin(Long orderId, DeliveryStatus newStatus) {
        Delivery delivery =
                deliveryRepository.findByOrderId(orderId).orElseThrow(() -> new DeliveryNotFoundException(orderId));
        delivery.updateStatus(newStatus);

        return DeliveryResponse.from(delivery);
    }
}
