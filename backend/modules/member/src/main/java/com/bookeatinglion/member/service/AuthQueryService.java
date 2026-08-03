package com.bookeatinglion.member.service;

import com.bookeatinglion.common.exception.BusinessException;
import com.bookeatinglion.common.exception.ErrorCode;
import com.bookeatinglion.common.security.JwtUtil;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.dto.AuthDto;
import com.bookeatinglion.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증(Auth) 도메인의 조회(Query) 위주 유스케이스를 담당하는 서비스.
 *
 * <p>로그인은 비밀번호를 검증하는 로직이 포함되어 있지만 회원 데이터를 변경하지는 않으므로,
 * 베타 프로젝트의 컨벤션과 동일하게 Query 서비스로 분류한다. 토큰 재발급 역시
 * (서버 측에 Refresh Token을 별도로 저장하지 않는 stateless 방식이므로) 회원 데이터를
 * 변경하지 않아 Query 서비스에 둔다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthQueryService {

    /** JWT의 {@code type} 클레임 중 Refresh Token을 나타내는 값. */
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * {@code POST /api/auth/login} 요청을 처리한다.
     *
     * @param request 로그인 요청 바디(아이디/비밀번호)
     * @return 새로 발급된 Access/Refresh Token과 회원 요약 정보
     * @throws BusinessException {@link ErrorCode#INVALID_CREDENTIALS} - 아이디가 없거나 비밀번호가 일치하지 않는 경우
     */
    public AuthDto.TokenResponse login(AuthDto.LoginRequest request) {
        Member member = memberRepository.findByUsername(request.getUsername())
                // 아이디가 존재하지 않는 경우와 비밀번호가 틀린 경우를 같은 메시지로 응답하여
                // 아이디 존재 여부가 외부에 노출되지 않도록 한다.
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokenResponse(member);
    }

    /**
     * {@code POST /api/auth/refresh} 요청을 처리한다.
     *
     * <p>Refresh Token 자체의 서명/만료를 검증하는 것 외에 서버 측에 별도의 저장소(Redis 등)를
     * 두지 않는 stateless 방식으로 구현했다. Access Token뿐 아니라 Refresh Token도 함께
     * 재발급(rotate)하여, 탈취된 Refresh Token이 재사용되는 기간을 최소화한다.</p>
     *
     * @param request 리프레시 요청 바디(refreshToken)
     * @return 새로 발급된 Access/Refresh Token과 회원 요약 정보
     * @throws BusinessException {@link ErrorCode#INVALID_REFRESH_TOKEN} -
     *         토큰이 만료/변조되었거나, {@code type} 클레임이 "refresh"가 아니거나,
     *         subject에 해당하는 회원을 찾을 수 없는 경우
     */
    public AuthDto.TokenResponse refresh(AuthDto.RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtUtil.parseRefreshClaims(request.getRefreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치, 만료, 형식 오류 등 jjwt가 던질 수 있는 모든 예외를 하나의 에러로 통일한다.
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Access Token으로 재발급을 시도하는 것을 차단하기 위해 type 클레임을 반드시 확인한다.
        if (!TOKEN_TYPE_REFRESH.equals(String.valueOf(claims.get("type")))) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String username = claims.getSubject();
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokenResponse(member);
    }

    /**
     * 주어진 회원에 대해 새로운 Access/Refresh Token 쌍을 발급하고 응답 DTO로 변환한다.
     *
     * @param member 토큰을 발급할 대상 회원
     * @return Access/Refresh Token과 회원 요약 정보를 담은 응답
     */
    private AuthDto.TokenResponse issueTokenResponse(Member member) {
        String role = member.getRole().name();
        String accessToken = jwtUtil.createAccessToken(member.getUsername(), role);
        String refreshToken = jwtUtil.createRefreshToken(member.getUsername(), role);

        return AuthDto.TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .memberId(member.getId())
                .username(member.getUsername())
                .role(role)
                .build();
    }
}
