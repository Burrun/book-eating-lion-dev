package com.bookeatinglion.order.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.delivery.domain.Delivery;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.delivery.dto.DeliveryResponse;
import com.bookeatinglion.order.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.order.delivery.exception.InvalidDeliveryStatusTransitionException;
import com.bookeatinglion.order.delivery.exception.UnauthorizedDeliveryAccessException;
import com.bookeatinglion.order.delivery.repository.DeliveryRepository;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MemberRepository 목이 사라진 것이 이 테스트의 핵심 변화다.
 * 소유권 검증이 JWT 클레임의 memberId 로 바뀌면서 member-service 의존이 없어졌다.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String OTHER_MEMBER_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private OrderRepository orderRepository;

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

    private Order orderOwnedBy(String memberId) {
        Order order = mock(Order.class);
        when(order.getMemberId()).thenReturn(memberId);
        return order;
    }

    @Test
    void 본인_주문의_배송_상태를_조회한다() {
        // orderOwnedBy() 안에서 스터빙하므로 when(...) 인자로 인라인하면 안 된다
        // (Mockito 가 스터빙 중첩으로 보고 UnfinishedStubbingException 을 던진다).
        Order order = orderOwnedBy(MEMBER_ID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryRepository.findByOrderId(100L)).thenReturn(Optional.of(delivery(100L)));

        DeliveryResponse response = deliveryService.getDeliveryByOrder(MEMBER_ID, 100L);

        assertThat(response.orderId()).isEqualTo(100L);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void 존재하지_않는_주문이면_예외를_던진다() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getDeliveryByOrder(MEMBER_ID, 999L))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void 타인의_주문을_조회하면_권한_예외를_던진다() {
        Order order = orderOwnedBy(MEMBER_ID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.getDeliveryByOrder(OTHER_MEMBER_ID, 100L))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class);
    }

    @Test
    void 주문은_존재하지만_배송정보가_없으면_예외를_던진다() {
        Order order = orderOwnedBy(MEMBER_ID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryRepository.findByOrderId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getDeliveryByOrder(MEMBER_ID, 100L))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void 본인_주문의_배송_상태를_변경한다() {
        Order order = orderOwnedBy(MEMBER_ID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        Delivery delivery = Delivery.builder().orderId(100L).build();
        when(deliveryRepository.findByOrderId(100L)).thenReturn(Optional.of(delivery));

        DeliveryResponse response = deliveryService.updateDeliveryStatus(MEMBER_ID, 100L, DeliveryStatus.SHIPPED);

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    void 타인의_주문은_배송_상태를_변경할_수_없다() {
        Order order = orderOwnedBy(MEMBER_ID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(
                        () -> deliveryService.updateDeliveryStatus(OTHER_MEMBER_ID, 100L, DeliveryStatus.SHIPPED))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class);
    }

    @Test
    void 존재하지_않는_주문의_배송_상태는_변경할_수_없다() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(MEMBER_ID, 999L, DeliveryStatus.SHIPPED))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void 잘못된_상태_전이는_예외를_던진다() {
        Order order = orderOwnedBy(MEMBER_ID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryRepository.findByOrderId(100L)).thenReturn(Optional.of(delivery(100L))); // IN_TRANSIT

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(MEMBER_ID, 100L, DeliveryStatus.PENDING))
                .isInstanceOf(InvalidDeliveryStatusTransitionException.class);
    }
}
