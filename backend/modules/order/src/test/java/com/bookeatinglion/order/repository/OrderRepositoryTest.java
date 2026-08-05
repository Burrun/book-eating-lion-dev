package com.bookeatinglion.order.repository;

import com.bookeatinglion.common.domain.BaseEntity;
import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.domain.Order;
import com.bookeatinglion.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    private Order order(Long memberId, OrderStatus status, long totalAmount) throws Exception {
        Order order = Order.builder()
                .memberId(memberId).bookId(1L).orderStatus(status).totalAmount(totalAmount)
                .build();
        return orderRepository.save(order);
    }

    private Order orderWithCreatedAt(Long memberId, OrderStatus status, long totalAmount,
                                      LocalDateTime createdAt) throws Exception {
        Order order = Order.builder()
                .memberId(memberId).bookId(1L).orderStatus(status).totalAmount(totalAmount)
                .build();
        // createdAt은 @Column(updatable = false)라 save() 이후 값을 바꿔도 UPDATE에 반영되지 않으므로,
        // 최초 INSERT 전에 리플렉션으로 세팅해야 한다.
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(order, createdAt);
        return orderRepository.save(order);
    }

    @Test
    void 상태별_주문목록을_조회한다() throws Exception {
        order(1L, OrderStatus.PAID, 10000L);
        order(2L, OrderStatus.PENDING_PAYMENT, 20000L);
        order(3L, OrderStatus.PAID, 30000L);

        Page<Order> result = orderRepository.findByOrderStatus(OrderStatus.PAID, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Order::getOrderStatus)
                .containsOnly(OrderStatus.PAID);
    }

    @Test
    void 최근_5건의_주문을_생성일_내림차순으로_조회한다() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 7; i++) {
            orderWithCreatedAt((long) i, OrderStatus.PAID, 10000L, base.plusDays(i));
        }

        List<Order> result = orderRepository.findTop5ByOrderByCreatedAtDesc();

        assertThat(result).hasSize(5);
        assertThat(result.get(0).getMemberId()).isEqualTo(6L);
        assertThat(result.get(4).getMemberId()).isEqualTo(2L);
    }

    @Test
    void 취소_환불_상태를_제외한_주문총액을_합산한다() throws Exception {
        order(1L, OrderStatus.PAID, 10000L);
        order(2L, OrderStatus.SHIPPED, 20000L);
        order(3L, OrderStatus.CANCELLED, 5000L);
        order(4L, OrderStatus.REFUNDED, 7000L);
        order(5L, OrderStatus.PENDING_PAYMENT, 3000L);

        long total = orderRepository.sumTotalAmountByOrderStatusNotIn(
                EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED,
                        OrderStatus.CANCELLED, OrderStatus.REFUNDED));

        assertThat(total).isEqualTo(30000L);
    }
}
