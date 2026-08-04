package com.bookeatinglion.delivery.service;

import com.bookeatinglion.delivery.domain.Delivery;
import com.bookeatinglion.delivery.dto.DeliveryResponse;
import com.bookeatinglion.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.delivery.exception.UnauthorizedDeliveryAccessException;
import com.bookeatinglion.delivery.repository.DeliveryRepository;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.repository.MemberRepository;
import com.bookeatinglion.order.domain.Order;
import com.bookeatinglion.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;

    public DeliveryResponse getDeliveryByOrder(String memberSub, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));

        Member member = memberRepository.findByCognitoSub(memberSub)
                .orElseThrow(() -> new UnauthorizedDeliveryAccessException(orderId));

        if (!order.getMemberId().equals(member.getId())) {
            throw new UnauthorizedDeliveryAccessException(orderId);
        }

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));

        return DeliveryResponse.from(delivery);
    }
}
