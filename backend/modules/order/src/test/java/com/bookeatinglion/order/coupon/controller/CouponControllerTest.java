package com.bookeatinglion.order.coupon.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.coupon.dto.MemberCouponView;
import com.bookeatinglion.order.coupon.exception.CouponAlreadyIssuedException;
import com.bookeatinglion.order.coupon.exception.CouponExpiredException;
import com.bookeatinglion.order.coupon.exception.CouponNotFoundException;
import com.bookeatinglion.order.coupon.service.CouponService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {CouponController.class, CouponExceptionHandler.class})
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class CouponControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("member-sub-1").claim("member_id", MEMBER_ID));
    }

    private MemberCouponView memberCouponView() {
        return new MemberCouponView(
                1L,
                10L,
                "WELCOME3000",
                "신규 가입 할인",
                3000,
                10000,
                LocalDateTime.now().plusDays(30));
    }

    @Test
    void 보유_쿠폰_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(couponService.getAvailableCoupons(MEMBER_ID)).thenReturn(List.of(memberCouponView()));

        mockMvc.perform(get("/api/coupons/me").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].couponCode").value("WELCOME3000"));
    }

    @Test
    void 쿠폰_등록은_200과_등록된_쿠폰을_반환한다() throws Exception {
        when(couponService.registerCoupon(MEMBER_ID, "WELCOME3000")).thenReturn(memberCouponView());

        mockMvc.perform(post("/api/coupons/register")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WELCOME3000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponCode").value("WELCOME3000"));
    }

    @Test
    void 코드가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/coupons/register")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 존재하지_않는_코드는_404를_반환한다() throws Exception {
        when(couponService.registerCoupon(MEMBER_ID, "NO-SUCH")).thenThrow(new CouponNotFoundException("NO-SUCH"));

        mockMvc.perform(post("/api/coupons/register")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NO-SUCH\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 만료된_쿠폰은_400을_반환한다() throws Exception {
        when(couponService.registerCoupon(MEMBER_ID, "EXPIRED")).thenThrow(new CouponExpiredException("EXPIRED"));

        mockMvc.perform(post("/api/coupons/register")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EXPIRED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 이미_보유한_쿠폰은_409를_반환한다() throws Exception {
        when(couponService.registerCoupon(MEMBER_ID, "DUP")).thenThrow(new CouponAlreadyIssuedException("DUP"));

        mockMvc.perform(post("/api/coupons/register")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
