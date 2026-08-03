package com.bookeatinglion.member.domain;

/**
 * 회원의 시스템 인가 권한.
 *
 * <p>Spring Security의 {@code hasRole(...)} 규칙과 연동되며, 실제 인가 검사 시에는
 * {@code "ROLE_" + role.name()} 형태의 {@code GrantedAuthority}로 변환되어 사용된다.</p>
 */
public enum Role {

    /** 일반 회원. */
    USER,

    /** 관리자. {@code /api/admin/**} 등 관리자 전용 API 접근이 허용된다. */
    ADMIN
}
