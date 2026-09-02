package com.bookeatinglion.order.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class OrderItemRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Test
    void 주문ID로_주문상품_목록을_조회한다() {
        Order order = orderRepository.save(new Order(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "홍길동", "010-0000-0000", "06236", "서울", null, null, 30000));
        orderItemRepository.save(new OrderItem(order, 100L, "책1", 2, 10000));
        orderItemRepository.save(new OrderItem(order, 200L, "책2", 1, 10000));

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        assertThat(items).hasSize(2).extracting(OrderItem::getBookId).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void 주문상품이_없으면_빈_목록을_반환한다() {
        List<OrderItem> items = orderItemRepository.findByOrderId(999L);

        assertThat(items).isEmpty();
    }
}
