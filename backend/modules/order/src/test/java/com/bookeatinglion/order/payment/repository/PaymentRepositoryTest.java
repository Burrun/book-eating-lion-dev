package com.bookeatinglion.order.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.repository.OrderRepository;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class PaymentRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void 주문ID로_결제정보를_조회한다() {
        Order order = orderRepository.save(
                new Order("a1b2c3d4-e5f6-7890-abcd-ef1234567890", "홍길동", "010-0000-0000", "06236", "서울", 30000));
        paymentRepository.save(Payment.approved(order, 10L, PaymentMethod.VIRTUAL_CARD, 30000, "AP-1", null, "idem-1"));

        Optional<Payment> result = paymentRepository.findByOrderId(order.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getAmount()).isEqualTo(30000);
    }

    @Test
    void 결제정보가_없으면_빈값을_반환한다() {
        Optional<Payment> result = paymentRepository.findByOrderId(999L);

        assertThat(result).isEmpty();
    }
}
