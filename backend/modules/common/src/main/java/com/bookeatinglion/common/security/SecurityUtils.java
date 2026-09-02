package com.bookeatinglion.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT 클레임 접근자.
 *
 * memberId / nickname 을 클레임에서 읽는 이유는 계획서 판단 ③ 표의
 * "order → member 회원 검증: JWT 클레임으로 대체 시 호출 자체 제거" 때문이다.
 * 이 클레임들이 없으면 다른 서비스가 회원 정보를 확인하려고 member-service 를
 * 동기 호출해야 하고, 그 순간 인증이 모든 요청의 임계경로가 된다.
 *
 * 클레임은 member-service 가 Cognito PreTokenGeneration 단계에서 주입한다.
 *
 * currentMemberId() 는 하위 호환을 위해 기존 커스텀 클레임(member_id, Long) 기반 그대로
 * 유지한다 — member/ai 모듈이 아직 이 값을 쓴다. Cognito sub(UUID String) 기반으로
 * 전환한 도메인(order-service)은 currentMemberSub() 를 쓴다.
 */
public final class SecurityUtils {

    public static final String CLAIM_MEMBER_ID = "member_id";
    public static final String CLAIM_NICKNAME = "nickname";

    private SecurityUtils() {}

    /** Cognito 표준 클레임 sub(subject) — UUID 문자열 그대로. order-service 가 쓴다. */
    public static String currentMemberSub() {
        return currentJwt().getSubject();
    }

    /**
     * 다른 서비스가 소유권을 검증할 때 쓰는 값. member-service 호출이 필요 없다.
     */
    public static Long currentMemberId() {
        Object claim = currentJwt().getClaim(CLAIM_MEMBER_ID);
        if (claim == null) {
            throw new IllegalStateException("JWT 에 " + CLAIM_MEMBER_ID + " 클레임이 없습니다.");
        }
        if (claim instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(claim.toString());
    }

    /**
     * 리뷰 작성자 표시용 스냅샷. 여기서 읽어 두면 닉네임 조회를 위해
     * member-service 를 부를 일이 없다.
     */
    public static String currentNickname() {
        Object claim = currentJwt().getClaim(CLAIM_NICKNAME);
        return claim == null ? null : claim.toString();
    }

    private static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("인증된 JWT 정보를 찾을 수 없습니다.");
    }
}
