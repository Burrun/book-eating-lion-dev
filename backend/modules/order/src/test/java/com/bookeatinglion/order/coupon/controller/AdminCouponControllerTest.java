package com.bookeatinglion.order.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.coupon.dto.CouponResponse;
import com.bookeatinglion.order.coupon.exception.CouponCodeDuplicatedException;
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

// 이 모듈 테스트는 order-api 의 SecurityConfig(hasRole("ADMIN"))를 로드하지 않는다 —
// 컨트롤러/서비스 동작만 검증하고, 실제 ADMIN 권한 게이트는 order-api 통합 레벨에서 보장된다.
@WebMvcTest(controllers = {AdminCouponController.class, CouponExceptionHandler.class})
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class AdminCouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("admin-sub"));
    }

    private CouponResponse couponResponse() {
        return new CouponResponse(
                1L, "WELCOME3000", "신규 가입 할인", 3000, 10000, LocalDateTime.now().plusDays(30));
    }

    @Test
    void 쿠폰_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(couponService.getAllCoupons()).thenReturn(List.of(couponResponse()));

        mockMvc.perform(get("/api/coupons/admin").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].couponCode").value("WELCOME3000"));
    }

    @Test
    void 쿠폰_등록은_201과_등록된_쿠폰을_반환한다() throws Exception {
        when(couponService.createCoupon(any())).thenReturn(couponResponse());

        mockMvc.perform(
                        post("/api/coupons/admin")
                                .with(authenticated())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"couponCode":"WELCOME3000","couponName":"신규 가입 할인",
                                 "discountAmount":3000,"minimumOrderAmount":10000,
                                 "expiresAt":"2099-12-31T23:59:59"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponCode").value("WELCOME3000"));
    }

    @Test
    void 등록시_코드가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(
                        post("/api/coupons/admin")
                                .with(authenticated())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"couponCode":"","couponName":"신규 가입 할인",
                                 "discountAmount":3000,"minimumOrderAmount":10000,
                                 "expiresAt":"2099-12-31T23:59:59"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 중복된_코드_등록은_409를_반환한다() throws Exception {
        when(couponService.createCoupon(any())).thenThrow(new CouponCodeDuplicatedException("WELCOME3000"));

        mockMvc.perform(
                        post("/api/coupons/admin")
                                .with(authenticated())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"couponCode":"WELCOME3000","couponName":"신규 가입 할인",
                                 "discountAmount":3000,"minimumOrderAmount":10000,
                                 "expiresAt":"2099-12-31T23:59:59"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 존재하지_않는_쿠폰_수정은_404를_반환한다() throws Exception {
        when(couponService.updateCoupon(eq(999L), any())).thenThrow(new CouponNotFoundException(999L));

        mockMvc.perform(
                        patch("/api/coupons/admin/999")
                                .with(authenticated())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"couponName":"수정된 이름","discountAmount":5000,
                                 "minimumOrderAmount":20000,"expiresAt":"2020-01-01T00:00:00"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 만료일을_과거로_수정하는_요청도_검증을_통과한다() throws Exception {
        when(couponService.updateCoupon(eq(1L), any())).thenReturn(couponResponse());

        mockMvc.perform(
                        patch("/api/coupons/admin/1")
                                .with(authenticated())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"couponName":"종료된 쿠폰","discountAmount":3000,
                                 "minimumOrderAmount":10000,"expiresAt":"2020-01-01T00:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
