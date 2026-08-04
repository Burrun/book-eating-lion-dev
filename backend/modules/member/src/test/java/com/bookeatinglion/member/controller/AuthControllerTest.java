package com.bookeatinglion.member.controller;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.dto.LoginRequest;
import com.bookeatinglion.member.dto.RefreshRequest;
import com.bookeatinglion.member.dto.SignupRequest;
import com.bookeatinglion.member.dto.SignupResponse;
import com.bookeatinglion.member.dto.TokenResponse;
import com.bookeatinglion.member.exception.DuplicateEmailException;
import com.bookeatinglion.member.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void 회원가입은_200과_회원정보를_반환한다() throws Exception {
        when(authService.signup(any())).thenReturn(new SignupResponse(1L, "lion@bookeating.com", "책먹는사자"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("lion@bookeating.com", "password1234", "책먹는사자"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("lion@bookeating.com"));
    }

    @Test
    void 이미_가입된_이메일이면_409를_반환한다() throws Exception {
        when(authService.signup(any())).thenThrow(new DuplicateEmailException("lion@bookeating.com"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("lion@bookeating.com", "password1234", "책먹는사자"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 로그인은_200과_토큰을_반환한다() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse("access-token", "refresh-token", "Bearer", 3600));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("lion@bookeating.com", "password1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void 토큰_재발급은_200과_새_토큰을_반환한다() throws Exception {
        when(authService.refresh(any())).thenReturn(new TokenResponse("new-access-token", "refresh-token", "Bearer", 3600));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }
}
