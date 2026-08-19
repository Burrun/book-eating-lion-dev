package com.bookeatinglion.member.subscription.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;
import com.bookeatinglion.member.subscription.dto.SubscriptionStatusResponse;
import com.bookeatinglion.member.subscription.service.SubscriptionService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /internal/** 은 실제 SecurityConfig(apps/member-api)에서 permitAll 이지만, 이 모듈 슬라이스
 * 테스트에는 그 설정이 로드되지 않아 Spring Security 기본값(전부 인증 요구)이 적용된다.
 * addFilters=false 로 시큐리티 필터 자체를 꺼서 실제 운영 동작(무인증 허용)과 맞춘다.
 */
@WebMvcTest(controllers = {InternalSubscriptionController.class, SubscriptionExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class InternalSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    void 구독_상태조회는_ApiResponse로_감싸지_않고_원본을_반환한다() throws Exception {
        when(subscriptionService.getSubscriptionStatus("member-sub-1"))
                .thenReturn(new SubscriptionStatusResponse(
                        "member-sub-1",
                        true,
                        SubscriptionStatus.ACTIVE,
                        LocalDateTime.now().plusMonths(1)));

        mockMvc.perform(get("/internal/members/member-sub-1/subscription-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.memberId").value("member-sub-1"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void 구독이_없으면_subscribed_false를_반환한다() throws Exception {
        when(subscriptionService.getSubscriptionStatus("member-sub-2"))
                .thenReturn(SubscriptionStatusResponse.none("member-sub-2"));

        mockMvc.perform(get("/internal/members/member-sub-2/subscription-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));
    }
}
