package com.bookeatinglion.order.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.order.domain.OrderStatus;
import com.bookeatinglion.order.order.dto.OrderResponse;
import com.bookeatinglion.order.order.dto.Recipient;
import com.bookeatinglion.order.order.exception.OrderCannotBeCancelledException;
import com.bookeatinglion.order.order.exception.OrderCannotBeRefundedException;
import com.bookeatinglion.order.order.exception.OrderCannotBeReturnedException;
import com.bookeatinglion.order.order.exception.OrderNotFoundException;
import com.bookeatinglion.order.order.exception.OutOfStockException;
import com.bookeatinglion.order.order.exception.UnauthorizedOrderAccessException;
import com.bookeatinglion.order.order.service.OrderService;
import com.bookeatinglion.order.payment.exception.PaymentDeclinedException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {OrderController.class, OrderExceptionHandler.class})
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class OrderControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("member-sub-1").claim("member_id", MEMBER_ID));
    }

    private OrderResponse orderResponse(OrderStatus status) {
        return new OrderResponse(
                1L, status, new Recipient("홍길동", "010-0000-0000", "06236", "서울"), 20000, List.of(), null, null, null);
    }

    private OrderResponse pendingKakaoResponse() {
        return new OrderResponse(
                1L,
                OrderStatus.PENDING_PAYMENT,
                new Recipient("홍길동", "010-0000-0000", "06236", "서울"),
                20000,
                List.of(),
                null,
                "https://mockup-pg-web.kakao.com/redirect",
                null);
    }

    private static final String CREATE_ORDER_BODY = "{"
            + "\"items\":[{\"bookId\":100,\"quantity\":2}],"
            + "\"recipient\":{\"name\":\"홍길동\",\"phone\":\"010-0000-0000\",\"postalCode\":\"06236\",\"address\":\"서울\"},"
            + "\"paymentMethod\":\"KAKAO_PAY\""
            + "}";

    @Test
    void 주문_생성은_200과_데이터를_반환한다() throws Exception {
        when(orderService.createOrder(eq(MEMBER_ID), any())).thenReturn(orderResponse(OrderStatus.PAID));

        mockMvc.perform(post("/api/orders")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ORDER_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));
    }

    @Test
    void 카카오페이_주문_생성은_PENDING_PAYMENT와_리다이렉트URL을_반환한다() throws Exception {
        when(orderService.createOrder(eq(MEMBER_ID), any())).thenReturn(pendingKakaoResponse());

        mockMvc.perform(post("/api/orders")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ORDER_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.nextRedirectUrl").value("https://mockup-pg-web.kakao.com/redirect"));
    }

    @Test
    void 재고부족이면_400을_반환한다() throws Exception {
        when(orderService.createOrder(eq(MEMBER_ID), any())).thenThrow(new OutOfStockException(100L));

        mockMvc.perform(post("/api/orders")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ORDER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 결제거절이면_402를_반환한다() throws Exception {
        when(orderService.createOrder(eq(MEMBER_ID), any())).thenThrow(new PaymentDeclinedException("한도 초과"));

        mockMvc.perform(post("/api/orders")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ORDER_BODY))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void items가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[],"
                                + "\"recipient\":{\"name\":\"홍길동\",\"phone\":\"010-0000-0000\",\"postalCode\":\"06236\",\"address\":\"서울\"},"
                                + "\"paymentMethod\":\"KAKAO_PAY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 주문_상세조회는_200과_데이터를_반환한다() throws Exception {
        when(orderService.getOrder(MEMBER_ID, 1L)).thenReturn(orderResponse(OrderStatus.PAID));

        mockMvc.perform(get("/api/orders/1").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(1));
    }

    @Test
    void 존재하지_않는_주문_조회는_404를_반환한다() throws Exception {
        when(orderService.getOrder(MEMBER_ID, 999L)).thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/api/orders/999").with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 타인의_주문_조회는_403을_반환한다() throws Exception {
        when(orderService.getOrder(MEMBER_ID, 1L)).thenThrow(new UnauthorizedOrderAccessException(1L));

        mockMvc.perform(get("/api/orders/1").with(authenticated()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 주문_취소는_200과_취소된_데이터를_반환한다() throws Exception {
        when(orderService.cancelOrder(MEMBER_ID, 1L)).thenReturn(orderResponse(OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/orders/1/cancel").with(authenticated()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));
    }

    @Test
    void PAID가_아닌_주문_취소는_409를_반환한다() throws Exception {
        when(orderService.cancelOrder(MEMBER_ID, 1L)).thenThrow(new OrderCannotBeCancelledException(1L));

        mockMvc.perform(post("/api/orders/1/cancel").with(authenticated()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 반품_신청은_200과_RETURN_REQUESTED_데이터를_반환한다() throws Exception {
        when(orderService.requestReturn(MEMBER_ID, 1L, "단순 변심"))
                .thenReturn(orderResponse(OrderStatus.RETURN_REQUESTED));

        mockMvc.perform(post("/api/orders/1/return")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("RETURN_REQUESTED"));
    }

    @Test
    void reason이_비어있으면_반품_신청은_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/orders/1/return")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void PAID가_아닌_주문_반품_신청은_409를_반환한다() throws Exception {
        when(orderService.requestReturn(MEMBER_ID, 1L, "단순 변심")).thenThrow(new OrderCannotBeReturnedException(1L));

        mockMvc.perform(post("/api/orders/1/return")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 환불_처리는_200과_REFUNDED_데이터를_반환한다() throws Exception {
        when(orderService.refundOrder(MEMBER_ID, 1L)).thenReturn(orderResponse(OrderStatus.REFUNDED));

        mockMvc.perform(post("/api/orders/1/refund").with(authenticated()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"));
    }

    @Test
    void RETURN_REQUESTED가_아닌_주문_환불은_409를_반환한다() throws Exception {
        when(orderService.refundOrder(MEMBER_ID, 1L)).thenThrow(new OrderCannotBeRefundedException(1L));

        mockMvc.perform(post("/api/orders/1/refund").with(authenticated()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
