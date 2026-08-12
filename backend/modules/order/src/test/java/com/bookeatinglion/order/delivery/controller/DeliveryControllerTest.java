package com.bookeatinglion.order.delivery.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.delivery.dto.DeliveryResponse;
import com.bookeatinglion.order.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.order.delivery.exception.InvalidDeliveryStatusTransitionException;
import com.bookeatinglion.order.delivery.exception.UnauthorizedDeliveryAccessException;
import com.bookeatinglion.order.delivery.service.DeliveryService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DeliveryController.class)
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class DeliveryControllerTest {

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    private DeliveryResponse deliveryResponse() {
        return new DeliveryResponse(
                1L, 100L, "CJ대한통운", "123456789", DeliveryStatus.IN_TRANSIT, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * 소유권 검증에 필요한 값은 Cognito 표준 클레임인 sub(subject) 다.
     * 이 값이 없으면 order-service 는 회원을 식별하려고 member-service 를
     * 동기 호출해야 하고, 그 순간 인증이 결제의 임계경로가 된다.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject(MEMBER_ID));
    }

    @Test
    void 배송_상태_조회는_200과_데이터를_반환한다() throws Exception {
        when(deliveryService.getDeliveryByOrder(MEMBER_ID, 100L)).thenReturn(deliveryResponse());

        mockMvc.perform(get("/api/orders/100/delivery").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("123456789"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("IN_TRANSIT"));
    }

    @Test
    void 존재하지_않는_주문의_배송_상태_조회는_404를_반환한다() throws Exception {
        when(deliveryService.getDeliveryByOrder(MEMBER_ID, 999L)).thenThrow(new DeliveryNotFoundException(999L));

        mockMvc.perform(get("/api/orders/999/delivery").with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 타인의_주문_배송_상태_조회는_403을_반환한다() throws Exception {
        when(deliveryService.getDeliveryByOrder(MEMBER_ID, 100L))
                .thenThrow(new UnauthorizedDeliveryAccessException(100L));

        mockMvc.perform(get("/api/orders/100/delivery").with(authenticated()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 배송_상태_변경은_200과_변경된_데이터를_반환한다() throws Exception {
        when(deliveryService.updateDeliveryStatus(MEMBER_ID, 100L, DeliveryStatus.SHIPPED))
                .thenReturn(new DeliveryResponse(
                        1L, 100L, null, null, DeliveryStatus.SHIPPED, LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(patch("/api/orders/100/delivery/status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryStatus").value("SHIPPED"));
    }

    @Test
    void 잘못된_상태_전이는_409를_반환한다() throws Exception {
        when(deliveryService.updateDeliveryStatus(MEMBER_ID, 100L, DeliveryStatus.PENDING))
                .thenThrow(new InvalidDeliveryStatusTransitionException(
                        100L, DeliveryStatus.SHIPPED, DeliveryStatus.PENDING));

        mockMvc.perform(patch("/api/orders/100/delivery/status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void status가_없으면_배송_상태_변경은_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/orders/100/delivery/status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
