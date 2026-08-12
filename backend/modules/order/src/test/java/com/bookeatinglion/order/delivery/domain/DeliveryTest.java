package com.bookeatinglion.order.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookeatinglion.order.delivery.exception.InvalidDeliveryStatusTransitionException;
import org.junit.jupiter.api.Test;

class DeliveryTest {

    @Test
    void 기본_생성시_PENDING_상태다() {
        Delivery delivery = Delivery.builder().orderId(1L).build();

        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void PENDING에서_SHIPPED로_전이할_수_있다() {
        Delivery delivery = Delivery.builder().orderId(1L).build();

        delivery.updateStatus(DeliveryStatus.SHIPPED);

        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    void 순서대로_DELIVERED까지_전이할_수_있다() {
        Delivery delivery = Delivery.builder().orderId(1L).build();

        delivery.updateStatus(DeliveryStatus.SHIPPED);
        delivery.updateStatus(DeliveryStatus.IN_TRANSIT);
        delivery.updateStatus(DeliveryStatus.DELIVERED);

        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void 같은_상태로는_전이할_수_없다() {
        Delivery delivery =
                Delivery.builder().orderId(1L).deliveryStatus(DeliveryStatus.SHIPPED).build();

        assertThatThrownBy(() -> delivery.updateStatus(DeliveryStatus.SHIPPED))
                .isInstanceOf(InvalidDeliveryStatusTransitionException.class);
    }

    @Test
    void 이전_상태로_역행할_수_없다() {
        Delivery delivery =
                Delivery.builder().orderId(1L).deliveryStatus(DeliveryStatus.IN_TRANSIT).build();

        assertThatThrownBy(() -> delivery.updateStatus(DeliveryStatus.PENDING))
                .isInstanceOf(InvalidDeliveryStatusTransitionException.class);
    }

    @Test
    void 중간_단계를_건너뛸_수_없다() {
        Delivery delivery = Delivery.builder().orderId(1L).build(); // PENDING

        assertThatThrownBy(() -> delivery.updateStatus(DeliveryStatus.DELIVERED))
                .isInstanceOf(InvalidDeliveryStatusTransitionException.class);
    }

    @Test
    void DELIVERED는_종단_상태라_더이상_전이할_수_없다() {
        Delivery delivery =
                Delivery.builder().orderId(1L).deliveryStatus(DeliveryStatus.DELIVERED).build();

        assertThatThrownBy(() -> delivery.updateStatus(DeliveryStatus.DELIVERED))
                .isInstanceOf(InvalidDeliveryStatusTransitionException.class);
    }
}
