package com.bookeatinglion.api.controller;

import com.bookeatinglion.common.response.ApiResponse;
import com.bookeatinglion.member.dto.MemberDto;
import com.bookeatinglion.member.security.CustomUserDetails;
import com.bookeatinglion.member.service.MemberCommandService;
import com.bookeatinglion.member.service.MemberQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberController {

    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;

    @GetMapping
    public ResponseEntity<ApiResponse<MemberDto.MemberResponse>> getMe(
            @AuthenticationPrincipal CustomUserDetails principal) {
        MemberDto.MemberResponse response = memberQueryService.getMe(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<MemberDto.MemberResponse>> updateMe(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody MemberDto.MemberUpdateRequest request) {
        MemberDto.MemberResponse response = memberCommandService.updateMe(principal.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/grade")
    public ResponseEntity<ApiResponse<MemberDto.MemberGradeResponse>> getGrade(
            @AuthenticationPrincipal CustomUserDetails principal) {
        MemberDto.MemberGradeResponse response = memberQueryService.getGrade(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
