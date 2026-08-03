package com.bookeatinglion.member.domain;

/**
 * 회원의 프리미엄 멤버십 등급.
 *
 * <p>{@link Role}(USER/ADMIN)이 "시스템 상의 권한"을 나타내는 것과 달리,
 * {@code MemberGrade}는 "서비스 상의 등급/혜택 수준"을 나타낸다.
 * 예를 들어 관리자(ADMIN)도 BASIC 등급일 수 있고, 일반 사용자(USER)도 PREMIUM 등급일 수 있다.</p>
 */
public enum MemberGrade {

    /** 별도 결제 없이 가입 시 기본으로 부여되는 등급. */
    BASIC,

    /** {@code premium_memberships} 결제를 통해 승급되는 유료 등급. */
    PREMIUM
}
