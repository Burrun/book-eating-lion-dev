package com.bookeatinglion.delivery.service;

import com.bookeatinglion.delivery.domain.Delivery;
import com.bookeatinglion.delivery.domain.DeliveryStatus;
import com.bookeatinglion.delivery.dto.DeliveryResponse;
import com.bookeatinglion.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.delivery.exception.UnauthorizedDeliveryAccessException;
import com.bookeatinglion.delivery.repository.DeliveryRepository;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.repository.MemberRepository;
import com.bookeatinglion.order.domain.Order;
import com.bookeatinglion.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private DeliveryService deliveryService;

    private Delivery delivery(Long orderId) {
        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .courierCompany("CJ대한통운")
                .trackingNumber("123456789")
                .deliveryStatus(DeliveryStatus.IN_TRANSIT)
                .build();
        ReflectionTestUtils.setField(delivery, "id", 1L);
        return delivery;
    }

    private Order orderOwnedBy(Long memberId) {
        Order order = mock(Order.class);
        when(order.getMemberId()).thenReturn(memberId);
        return order;
    }

    private Member member(Long id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        return member;
    }

    @Test
    void 본인_주문의_배송_상태를_조회한다() {
        Order order = orderOwnedBy(1L);
        Member member = member(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(memberRepository.findByCognitoSub("member-sub-1")).thenReturn(Optional.of(member));
        when(deliveryRepository.findByOrderId(100L)).thenReturn(Optional.of(delivery(100L)));

        DeliveryResponse response = deliveryService.getDeliveryByOrder("member-sub-1", 100L);

        assertThat(response.orderId()).isEqualTo(100L);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void 존재하지_않는_주문이면_예외를_던진다() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getDeliveryByOrder("member-sub-1", 999L))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void 타인의_주문을_조회하면_권한_예외를_던진다() {
        Order order = orderOwnedBy(1L);
        Member member = member(2L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(memberRepository.findByCognitoSub("member-sub-2")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> deliveryService.getDeliveryByOrder("member-sub-2", 100L))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class);
    }

    @Test
    void 주문은_존재하지만_배송정보가_없으면_예외를_던진다() {
        Order order = orderOwnedBy(1L);
        Member member = member(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(memberRepository.findByCognitoSub("member-sub-1")).thenReturn(Optional.of(member));
        when(deliveryRepository.findByOrderId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getDeliveryByOrder("member-sub-1", 100L))
                .isInstanceOf(DeliveryNotFoundException.class);
    }
}
