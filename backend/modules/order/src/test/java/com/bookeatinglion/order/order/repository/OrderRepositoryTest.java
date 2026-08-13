package com.bookeatinglion.order.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.order.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class OrderRepositoryTest {

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String OTHER_MEMBER_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

    @Autowired
    private OrderRepository orderRepository;

    private Order order(String memberId, int totalAmount) {
        return new Order(memberId, "홍길동", "010-0000-0000", "06236", "서울", totalAmount);
    }

    @Test
    void 회원ID로_본인_주문만_페이징_조회한다() {
        orderRepository.save(order(MEMBER_ID, 10000));
        orderRepository.save(order(MEMBER_ID, 20000));
        orderRepository.save(order(OTHER_MEMBER_ID, 30000));

        Page<Order> page = orderRepository.findByMemberId(MEMBER_ID, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Order::getMemberId).containsOnly(MEMBER_ID);
    }

    @Test
    void 페이지_크기만큼만_반환하고_전체_개수는_유지한다() {
        for (int i = 0; i < 5; i++) {
            orderRepository.save(order(MEMBER_ID, 1000 * i));
        }

        Page<Order> page = orderRepository.findByMemberId(MEMBER_ID, PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }
}
