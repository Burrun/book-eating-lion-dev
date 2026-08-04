package com.bookeatinglion.member.controller;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.domain.Gender;
import com.bookeatinglion.member.domain.MemberGrade;
import com.bookeatinglion.member.domain.Role;
import com.bookeatinglion.member.dto.MemberGradeResponse;
import com.bookeatinglion.member.dto.MemberResponse;
import com.bookeatinglion.member.dto.MemberUpdateRequest;
import com.bookeatinglion.member.exception.MemberNotFoundException;
import com.bookeatinglion.member.service.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        return new MemberResponse(1L, "lion@bookeating.com", "책먹는사자", "010-1234-5678",
                Gender.MALE, LocalDate.of(2000, 1, 1), Role.USER, MemberGrade.BRONZE, 0);
    }

    @Test
    void 내_정보를_조회한다() throws Exception {
        when(memberService.getMyProfile(SUB)).thenReturn(memberResponse());

        mockMvc.perform(get("/api/members/me").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("lion@bookeating.com"));
    }

    @Test
    void 내_정보를_수정한다() throws Exception {
        when(memberService.updateProfile(eq(SUB), any())).thenReturn(memberResponse());

        MemberUpdateRequest request = new MemberUpdateRequest("책먹는사자", "010-1234-5678", Gender.MALE, LocalDate.of(2000, 1, 1));

        mockMvc.perform(patch("/api/members/me")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("책먹는사자"));
    }

    @Test
    void 존재하지_않는_회원을_조회하면_404를_반환한다() throws Exception {
        when(memberService.getMyProfile(SUB)).thenThrow(new MemberNotFoundException(SUB));

        mockMvc.perform(get("/api/members/me").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 등급과_포인트를_조회한다() throws Exception {
        when(memberService.getGrade(SUB)).thenReturn(new MemberGradeResponse(MemberGrade.BRONZE, 0));

        mockMvc.perform(get("/api/members/me/grade").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("BRONZE"))
                .andExpect(jsonPath("$.data.point").value(0));
    }
}
