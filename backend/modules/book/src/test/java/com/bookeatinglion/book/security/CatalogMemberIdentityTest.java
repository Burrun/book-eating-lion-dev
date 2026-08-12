package com.bookeatinglion.book.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CatalogMemberIdentityTest {

    private final CatalogMemberIdentity memberIdentity = new CatalogMemberIdentity();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void Cognito_JWT의_sub를_회원_ID로_사용한다() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "34589d0c-c0f1-702d-0477-6afacc060eda"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(memberIdentity.requiredMemberId())
                .isEqualTo("34589d0c-c0f1-702d-0477-6afacc060eda");
    }

    @Test
    void 비회원은_optional_회원_ID가_없다() {
        assertThat(memberIdentity.optionalMemberId()).isNull();
        assertThatThrownBy(memberIdentity::requiredMemberId)
                .isInstanceOf(IllegalStateException.class);
    }
}
