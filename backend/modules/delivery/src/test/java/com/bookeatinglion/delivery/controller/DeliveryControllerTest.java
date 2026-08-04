package com.bookeatinglion.delivery.controller;

import com.bookeatinglion.delivery.DeliveryModuleTestApplication;
import com.bookeatinglion.delivery.domain.DeliveryStatus;
import com.bookeatinglion.delivery.dto.DeliveryResponse;
import com.bookeatinglion.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.delivery.exception.UnauthorizedDeliveryAccessException;
import com.bookeatinglion.delivery.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeliveryController.class)
@ContextConfiguration(classes = DeliveryModuleTestApplication.class)
class DeliveryControllerTest {

    private static final String SUB = "member-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    private DeliveryResponse deliveryResponse() {
        return new DeliveryResponse(1L, 100L, "CJ대한통운", "123456789", DeliveryStatus.IN_TRANSIT,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void 배송_상태_조회는_200과_데이터를_반환한다() throws Exception {
        when(deliveryService.getDeliveryByOrder(SUB, 100L)).thenReturn(deliveryResponse());

        mockMvc.perform(get("/api/orders/100/delivery").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("123456789"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("IN_TRANSIT"));
    }

    @Test
    void 존재하지_않는_주문의_배송_상태_조회는_404를_반환한다() throws Exception {
        when(deliveryService.getDeliveryByOrder(SUB, 999L)).thenThrow(new DeliveryNotFoundException(999L));

        mockMvc.perform(get("/api/orders/999/delivery").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 타인의_주문_배송_상태_조회는_403을_반환한다() throws Exception {
        when(deliveryService.getDeliveryByOrder(SUB, 100L)).thenThrow(new UnauthorizedDeliveryAccessException(100L));

        mockMvc.perform(get("/api/orders/100/delivery").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
