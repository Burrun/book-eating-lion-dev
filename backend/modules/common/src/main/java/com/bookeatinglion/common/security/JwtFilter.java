package com.bookeatinglion.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 매 HTTP 요청마다 {@code Authorization: Bearer {accessToken}} 헤더를 검사하여
 * 유효한 Access Token이면 Spring Security의 {@link SecurityContextHolder}에
 * 인증 정보를 채워 넣는 필터.
 *
 * <p>베타 프로젝트의 {@code JwtFilter}와 동일한 전략을 취한다: 이 필터는
 * "인증 성공/실패"를 직접 판단해 401을 내려주지 않는다. 대신, 토큰이 없거나
 * 유효하지 않으면 그냥 {@link SecurityContextHolder}를 비운 채로 다음 필터로 넘긴다.
 * 이후 {@code SecurityConfig}에 등록된 {@code authorizeHttpRequests} 규칙에 따라
 * 인증이 필요한 요청이면 Spring Security가 자동으로 401/403을 판단하고,
 * 그 결과는 {@code RestAuthenticationEntryPoint}/{@code RestAccessDeniedHandler}가
 * 공통 엔벨로프 포맷으로 응답한다.</p>
 *
 * <p>{@code UserDetailsService}는 인터페이스에만 의존하므로, 이 클래스는
 * 특정 도메인(Member)에 결합되지 않고 {@code common} 모듈에 위치할 수 있다.
 * 실제 구현체({@code MyUserDetailsService})는 {@code member} 모듈에서 제공한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * 요청 헤더에서 Access Token을 추출해 검증하고, 유효한 경우 인증 컨텍스트를 설정한다.
     *
     * @param request     현재 HTTP 요청
     * @param response    현재 HTTP 응답
     * @param filterChain 다음 필터로 요청/응답을 전달하기 위한 체인
     * @throws ServletException 서블릿 처리 중 오류가 발생한 경우
     * @throws IOException      입출력 처리 중 오류가 발생한 경우
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 이미 이전 단계에서 인증이 설정되어 있다면 중복 처리하지 않는다.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (!token.isBlank()) {
                try {
                    Claims claims = jwtUtil.parseAccessClaims(token);
                    String type = String.valueOf(claims.get("type"));

                    // refresh 토큰으로 API를 호출하는 것을 방지: 반드시 type=access여야 인증을 허용한다.
                    if ("access".equals(type)) {
                        String username = claims.getSubject();
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    // 토큰이 만료/변조되었거나 사용자를 찾을 수 없는 경우, 여기서 바로 401을 내려주지 않고
                    // SecurityContext만 비운 뒤 필터 체인을 계속 진행시킨다.
                    // 최종적으로 인증이 필요한 요청인지 여부는 SecurityConfig의 authorizeHttpRequests가 판단한다.
                    log.debug("Access Token 검증 실패: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
