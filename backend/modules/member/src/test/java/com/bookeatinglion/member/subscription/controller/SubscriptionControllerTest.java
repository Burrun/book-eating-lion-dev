package com.bookeatinglion.member.subscription.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.subscription.domain.PlanType;
import com.bookeatinglion.member.subscription.domain.SubscriptionStatus;
import com.bookeatinglion.member.subscription.dto.SubscriptionResponse;
import com.bookeatinglion.member.subscription.exception.AlreadySubscribedException;
import com.bookeatinglion.member.subscription.exception.SubscriptionNotFoundException;
import com.bookeatinglion.member.subscription.service.SubscriptionService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {SubscriptionController.class, SubscriptionExceptionHandler.class})
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class SubscriptionControllerTest {

    private static final String MEMBER_SUB = "member-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject(MEMBER_SUB));
    }

    private SubscriptionResponse subscriptionResponse(SubscriptionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new SubscriptionResponse(
                1L,
                PlanType.MONTHLY,
                status,
                now,
                now.plusMonths(1),
                status == SubscriptionStatus.CANCELLED ? now : null);
    }

    @Test
    void 구독_이력이_없으면_data_null과_200을_반환한다() throws Exception {
        when(subscriptionService.getMySubscription(MEMBER_SUB)).thenReturn(null);

        mockMvc.perform(get("/api/members/me/subscription").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 구독_조회는_200과_데이터를_반환한다() throws Exception {
        when(subscriptionService.getMySubscription(MEMBER_SUB))
                .thenReturn(subscriptionResponse(SubscriptionStatus.ACTIVE));

        mockMvc.perform(get("/api/members/me/subscription").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void 구독_시작은_201과_데이터를_반환한다() throws Exception {
        when(subscriptionService.subscribe(eq(MEMBER_SUB), eq(PlanType.MONTHLY)))
                .thenReturn(subscriptionResponse(SubscriptionStatus.ACTIVE));

        mockMvc.perform(post("/api/members/me/subscription")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planType\":\"MONTHLY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.planType").value("MONTHLY"));
    }

    @Test
    void 이미_구독중이면_409를_반환한다() throws Exception {
        when(subscriptionService.subscribe(eq(MEMBER_SUB), eq(PlanType.MONTHLY)))
                .thenThrow(new AlreadySubscribedException(MEMBER_SUB));

        mockMvc.perform(post("/api/members/me/subscription")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planType\":\"MONTHLY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 구독_해지는_200과_CANCELLED_상태를_반환한다() throws Exception {
        when(subscriptionService.cancel(MEMBER_SUB)).thenReturn(subscriptionResponse(SubscriptionStatus.CANCELLED));

        mockMvc.perform(delete("/api/members/me/subscription")
                        .with(authenticated())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void 활성_구독이_없으면_해지시_404를_반환한다() throws Exception {
        when(subscriptionService.cancel(MEMBER_SUB)).thenThrow(new SubscriptionNotFoundException(MEMBER_SUB));

        mockMvc.perform(delete("/api/members/me/subscription")
                        .with(authenticated())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
