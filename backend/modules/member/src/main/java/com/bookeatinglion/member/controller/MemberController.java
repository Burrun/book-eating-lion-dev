package com.bookeatinglion.member.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.member.dto.MemberGradeResponse;
import com.bookeatinglion.member.dto.MemberResponse;
import com.bookeatinglion.member.dto.MemberUpdateRequest;
import com.bookeatinglion.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(memberService.getMyProfile(jwt.getSubject()));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MemberUpdateRequest request) {
        return ApiResponse.success(memberService.updateProfile(jwt.getSubject(), request));
    }

    @GetMapping("/me/grade")
    public ApiResponse<MemberGradeResponse> getMyGrade(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(memberService.getGrade(jwt.getSubject()));
    }
}
