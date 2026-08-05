package com.bookeatinglion.api.config;

import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.domain.Role;
import com.bookeatinglion.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAwareJwtAuthenticationConverterTest {

    @Mock
    private MemberRepository memberRepository;

    private Jwt jwt(String sub) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", sub)
                .claim("scope", "read")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void ADMIN_회원이면_ROLE_ADMIN_권한이_추가된다() throws Exception {
        Member admin = adminMember();
        when(memberRepository.findByCognitoSub("admin-sub")).thenReturn(Optional.of(admin));
        AdminAwareJwtAuthenticationConverter converter = new AdminAwareJwtAuthenticationConverter(memberRepository);

        AbstractAuthenticationToken token = converter.convert(jwt("admin-sub"));

        assertThat(token.getAuthorities())
                .extracting(a -> a.getAuthority())
                .contains("ROLE_ADMIN", "SCOPE_read");
    }

    @Test
    void USER_회원이면_ROLE_ADMIN_권한이_추가되지_않는다() {
        when(memberRepository.findByCognitoSub("user-sub")).thenReturn(Optional.empty());
        AdminAwareJwtAuthenticationConverter converter = new AdminAwareJwtAuthenticationConverter(memberRepository);

        AbstractAuthenticationToken token = converter.convert(jwt("user-sub"));

        assertThat(token.getAuthorities())
                .extracting(a -> a.getAuthority())
                .doesNotContain("ROLE_ADMIN")
                .contains("SCOPE_read");
    }

    private Member adminMember() throws Exception {
        Member member = Member.register("admin-sub", "admin@a.com", "관리자");
        Field roleField = Member.class.getDeclaredField("role");
        roleField.setAccessible(true);
        roleField.set(member, Role.ADMIN);
        return member;
    }
}
