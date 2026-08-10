package com.bookeatinglion.order.delivery.repository;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.delivery.domain.Delivery;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class DeliveryRepositoryTest {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Test
    void 주문_ID로_배송_정보를_조회한다() {
        Delivery delivery = Delivery.builder()
                .orderId(100L)
                .courierCompany("CJ대한통운")
                .trackingNumber("123456789")
                .deliveryStatus(DeliveryStatus.IN_TRANSIT)
                .build();
        deliveryRepository.save(delivery);

        Optional<Delivery> result = deliveryRepository.findByOrderId(100L);

        assertThat(result).isPresent();
        assertThat(result.get().getCourierCompany()).isEqualTo("CJ대한통운");
        assertThat(result.get().getDeliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void 존재하지_않는_주문의_배송_정보는_빈값을_반환한다() {
        Optional<Delivery> result = deliveryRepository.findByOrderId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void 배송상태를_지정하지_않으면_PENDING이_기본값이다() {
        Delivery delivery = Delivery.builder()
                .orderId(200L)
                .build();

        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }
}
