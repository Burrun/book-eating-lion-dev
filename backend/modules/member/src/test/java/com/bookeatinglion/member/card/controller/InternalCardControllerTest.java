package com.bookeatinglion.member.card.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.card.dto.CardOperationResult;
import com.bookeatinglion.member.card.service.CardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /internal/** 은 실제 SecurityConfig(apps/member-api)에서 permitAll 이지만, 이 모듈 슬라이스
 * 테스트에는 그 설정이 로드되지 않아 Spring Security 기본값(전부 인증 요구)이 적용된다.
 * addFilters=false 로 시큐리티 필터 자체를 꺼서 실제 운영 동작(무인증 허용)과 맞춘다.
 */
@WebMvcTest(controllers = {InternalCardController.class, CardExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class InternalCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardService cardService;

    @Test
    void 한도_차감_요청은_200과_승인결과를_반환한다() throws Exception {
        when(cardService.deduct(1L, 5000L)).thenReturn(new CardOperationResult(true, null));

        mockMvc.perform(post("/internal/cards/1/deduct")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void 한도초과면_200과_거절결과를_반환한다() throws Exception {
        when(cardService.deduct(1L, 5000L)).thenReturn(new CardOperationResult(false, "한도가 부족합니다"));

        mockMvc.perform(post("/internal/cards/1/deduct")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false));
    }

    @Test
    void 한도_복구_요청은_200과_승인결과를_반환한다() throws Exception {
        when(cardService.restore(1L, 5000L)).thenReturn(new CardOperationResult(true, null));

        mockMvc.perform(post("/internal/cards/1/restore")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));
    }
}
