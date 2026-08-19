package com.bookeatinglion.order.delivery.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.delivery.dto.DeliveryResponse;
import com.bookeatinglion.order.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.order.delivery.exception.InvalidDeliveryStatusTransitionException;
import com.bookeatinglion.order.delivery.service.DeliveryService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// order-api 의 SecurityConfig(hasRole("ADMIN"))는 여기서 로드하지 않는다 — AdminCouponControllerTest 와
// 같은 이유다. 실제 ADMIN 권한 게이트 검증은 order-api 통합 레벨에서 보장된다.
@WebMvcTest(controllers = {AdminDeliveryController.class, DeliveryExceptionHandler.class})
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class AdminDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("admin-sub"));
    }

    @Test
    void 관리자_배송_상태_변경은_200과_변경된_데이터를_반환한다() throws Exception {
        when(deliveryService.updateDeliveryStatusAsAdmin(100L, DeliveryStatus.SHIPPED))
                .thenReturn(new DeliveryResponse(
                        1L, 100L, null, null, DeliveryStatus.SHIPPED, LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(patch("/api/orders/admin/100/delivery-status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryStatus").value("SHIPPED"));
    }

    @Test
    void 존재하지_않는_주문의_배송_상태_변경은_404를_반환한다() throws Exception {
        when(deliveryService.updateDeliveryStatusAsAdmin(999L, DeliveryStatus.SHIPPED))
                .thenThrow(new DeliveryNotFoundException(999L));

        mockMvc.perform(patch("/api/orders/admin/999/delivery-status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 잘못된_상태_전이는_409를_반환한다() throws Exception {
        when(deliveryService.updateDeliveryStatusAsAdmin(100L, DeliveryStatus.PENDING))
                .thenThrow(new InvalidDeliveryStatusTransitionException(
                        100L, DeliveryStatus.SHIPPED, DeliveryStatus.PENDING));

        mockMvc.perform(patch("/api/orders/admin/100/delivery-status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void status가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/orders/admin/100/delivery-status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
