package com.bookeatinglion.order.payment.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.order.controller.OrderExceptionHandler;
import com.bookeatinglion.order.order.domain.OrderStatus;
import com.bookeatinglion.order.order.dto.OrderResponse;
import com.bookeatinglion.order.order.dto.Recipient;
import com.bookeatinglion.order.order.exception.OrderNotFoundException;
import com.bookeatinglion.order.order.exception.OutOfStockException;
import com.bookeatinglion.order.order.exception.PaymentAlreadyProcessedException;
import com.bookeatinglion.order.order.exception.UnauthorizedOrderAccessException;
import com.bookeatinglion.order.order.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {PaymentController.class, OrderExceptionHandler.class})
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class PaymentControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("member-sub-1").claim("member_id", MEMBER_ID));
    }

    private OrderResponse paidResponse() {
        return new OrderResponse(
                1L,
                OrderStatus.PAID,
                new Recipient("홍길동", "010-0000-0000", "06236", "서울"),
                20000,
                List.of(),
                null,
                null,
                null);
    }

    @Test
    void 카카오페이_승인은_200과_PAID_주문을_반환한다() throws Exception {
        when(orderService.approveKakaoPay(MEMBER_ID, 1L, "pg-token-value")).thenReturn(paidResponse());

        mockMvc.perform(post("/api/payments/kakao/approve")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"pgToken\":\"pg-token-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));
    }

    @Test
    void pgToken이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/payments/kakao/approve")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"pgToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 존재하지_않는_주문이면_404를_반환한다() throws Exception {
        when(orderService.approveKakaoPay(MEMBER_ID, 999L, "pg-token")).thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(post("/api/payments/kakao/approve")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":999,\"pgToken\":\"pg-token\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 타인의_주문이면_403을_반환한다() throws Exception {
        when(orderService.approveKakaoPay(MEMBER_ID, 1L, "pg-token"))
                .thenThrow(new UnauthorizedOrderAccessException(1L));

        mockMvc.perform(post("/api/payments/kakao/approve")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"pgToken\":\"pg-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 이미_처리된_주문이면_409를_반환한다() throws Exception {
        when(orderService.approveKakaoPay(MEMBER_ID, 1L, "pg-token"))
                .thenThrow(new PaymentAlreadyProcessedException(1L));

        mockMvc.perform(post("/api/payments/kakao/approve")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"pgToken\":\"pg-token\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 재고부족이면_승인을_시도하지_않고_400을_반환한다() throws Exception {
        when(orderService.approveKakaoPay(MEMBER_ID, 1L, "pg-token")).thenThrow(new OutOfStockException(100L));

        mockMvc.perform(post("/api/payments/kakao/approve")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"pgToken\":\"pg-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
