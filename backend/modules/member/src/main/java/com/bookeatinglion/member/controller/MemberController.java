package com.bookeatinglion.member.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.member.domain.Role;
import com.bookeatinglion.member.dto.MemberResponse;
import com.bookeatinglion.member.dto.MemberUpdateRequest;
import com.bookeatinglion.member.service.MemberService;
import java.util.List;
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
        MemberResponse profile = memberService.getMyProfile(SecurityUtils.currentMemberSub());
        return ApiResponse.success(isAdmin(jwt) ? withRole(profile, Role.ADMIN) : profile);
    }

    @PatchMapping("/me")
    public ApiResponse<MemberResponse> updateMyProfile(@RequestBody MemberUpdateRequest request) {
        return ApiResponse.success(memberService.updateProfile(SecurityUtils.currentMemberSub(), request));
    }

    // ChatTicketController.isAgent() 와 동일한 패턴: cognito:groups 클레임을 요청마다 직접
    // 읽는다. DB의 Member.role은 가입 시 USER로 고정된 채 바뀌지 않으므로, Cognito 콘솔에서
    // 그룹을 바꾼 결과가 즉시 반영되려면 여기서 매번 JWT를 확인해야 한다.
    private static boolean isAdmin(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        return groups != null && groups.contains("ADMIN");
    }

    private static MemberResponse withRole(MemberResponse response, Role role) {
        return new MemberResponse(
                response.id(),
                response.email(),
                response.name(),
                response.phoneNumber(),
                response.gender(),
                response.birthDate(),
                role);
    }
}
