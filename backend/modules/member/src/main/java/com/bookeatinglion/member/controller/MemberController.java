package com.bookeatinglion.member.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.member.dto.MemberResponse;
import com.bookeatinglion.member.dto.MemberUpdateRequest;
import com.bookeatinglion.member.service.MemberService;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<MemberResponse> getMyProfile() {
        return ApiResponse.success(memberService.getMyProfile(SecurityUtils.currentMemberSub()));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberResponse> updateMyProfile(@RequestBody MemberUpdateRequest request) {
        return ApiResponse.success(memberService.updateProfile(SecurityUtils.currentMemberSub(), request));
    }
}
