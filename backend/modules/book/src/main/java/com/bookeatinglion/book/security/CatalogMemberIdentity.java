package com.bookeatinglion.book.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Catalog 서비스에서 검증된 Cognito sub를 회원 식별자로 제공한다. */
@Component
public class CatalogMemberIdentity {

    public String requiredMemberId() {
        String memberId = optionalMemberId();
        if (memberId == null) {
            throw new IllegalStateException("인증된 Cognito 사용자를 찾을 수 없습니다.");
        }
        return memberId;
    }

    public String optionalMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }
}
