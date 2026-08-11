package com.bookeatinglion.order.delivery.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.delivery.dto.DeliveryResponse;
import com.bookeatinglion.order.delivery.exception.DeliveryNotFoundException;
import com.bookeatinglion.order.delivery.exception.UnauthorizedDeliveryAccessException;
import com.bookeatinglion.order.delivery.service.DeliveryService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DeliveryController.class)
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class DeliveryControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    private DeliveryResponse deliveryResponse() {
        return new DeliveryResponse(
                1L, 100L, "CJ대한통운", "123456789", DeliveryStatus.IN_TRANSIT, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * 소유권 검증에 필요한 값이 sub 가 아니라 member_id 클레임으로 바뀌었다.
     * 이 클레임이 없으면 order-service 는 회원을 식별하려고 member-service 를
     * 동기 호출해야 하고, 그 순간 인증이 결제의 임계경로가 된다.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("member-sub-1").claim("member_id", MEMBER_ID));
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
}
