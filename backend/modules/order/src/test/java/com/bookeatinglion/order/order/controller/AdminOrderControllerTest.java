package com.bookeatinglion.order.order.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.order.domain.OrderStatus;
import com.bookeatinglion.order.order.dto.AdminOrderSummaryResponse;
import com.bookeatinglion.order.order.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// order-api 의 SecurityConfig(hasRole("ADMIN"))는 여기서 로드하지 않는다 — AdminCouponControllerTest 와
// 같은 이유다. 실제 ADMIN 권한 게이트 검증은 order-api 통합 레벨에서 보장된다.
@WebMvcTest(controllers = {AdminOrderController.class, OrderExceptionHandler.class})
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("admin-sub"));
    }

    private AdminOrderSummaryResponse summary(Long orderId, DeliveryStatus deliveryStatus) {
        return new AdminOrderSummaryResponse(orderId, "member-sub", "홍길동", OrderStatus.PAID, deliveryStatus, 20000);
    }

    @Test
    void 관리자_주문_목록_조회는_200과_페이징된_데이터를_반환한다() throws Exception {
        when(orderService.getAdminOrders(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L, DeliveryStatus.SHIPPED)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/orders/admin").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderId").value(1))
                .andExpect(jsonPath("$.data.content[0].deliveryStatus").value("SHIPPED"));
    }

    @Test
    void status_파라미터로_필터링해_조회한다() throws Exception {
        when(orderService.getAdminOrders(eq(OrderStatus.PAID), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L, DeliveryStatus.PENDING)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/orders/admin").param("status", "PAID").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderStatus").value("PAID"));
    }

    @Test
    void 배송정보가_없는_주문은_deliveryStatus가_null로_내려온다() throws Exception {
        when(orderService.getAdminOrders(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(summary(2L, null)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/orders/admin").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].deliveryStatus").value(nullValue()));
    }
}
