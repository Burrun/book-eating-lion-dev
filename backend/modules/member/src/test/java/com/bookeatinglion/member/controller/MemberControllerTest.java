package com.bookeatinglion.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.domain.Gender;
import com.bookeatinglion.member.domain.Role;
import com.bookeatinglion.member.dto.MemberResponse;
import com.bookeatinglion.member.dto.MemberUpdateRequest;
import com.bookeatinglion.member.exception.MemberNotFoundException;
import com.bookeatinglion.member.service.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MemberController.class)
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class MemberControllerTest {

    private static final String SUB = "sub-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemberService memberService;

    private MemberResponse memberResponse() {
        return new MemberResponse(
                SUB, "lion@bookeating.com", "책먹는사자", "010-1234-5678", Gender.MALE, LocalDate.of(2000, 1, 1), Role.USER);
    }

    @Test
    void 내_정보를_조회한다() throws Exception {
        when(memberService.getMyProfile(SUB)).thenReturn(memberResponse());

        mockMvc.perform(get("/api/members/me").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("lion@bookeating.com"))
                // id 는 DB PK 가 아니라 JWT sub 다.
                .andExpect(jsonPath("$.data.id").value(SUB));
    }

    @Test
    void 내_정보를_수정한다() throws Exception {
        when(memberService.updateProfile(eq(SUB), any())).thenReturn(memberResponse());

        MemberUpdateRequest request =
                new MemberUpdateRequest("책먹는사자", "010-1234-5678", Gender.MALE, LocalDate.of(2000, 1, 1));

        mockMvc.perform(patch("/api/members/me")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("책먹는사자"));
    }

    @Test
    void cognito_groups에_ADMIN이_있으면_role이_ADMIN으로_내려간다() throws Exception {
        when(memberService.getMyProfile(SUB)).thenReturn(memberResponse());

        mockMvc.perform(get("/api/members/me")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB).claim("cognito:groups", java.util.List.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void cognito_groups에_ADMIN이_없으면_DB의_role을_그대로_반환한다() throws Exception {
        when(memberService.getMyProfile(SUB)).thenReturn(memberResponse());

        mockMvc.perform(get("/api/members/me")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB).claim("cognito:groups", java.util.List.of("EDITOR")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void 존재하지_않는_회원을_조회하면_404를_반환한다() throws Exception {
        when(memberService.getMyProfile(SUB)).thenThrow(new MemberNotFoundException(SUB));

        mockMvc.perform(get("/api/members/me").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }
}
