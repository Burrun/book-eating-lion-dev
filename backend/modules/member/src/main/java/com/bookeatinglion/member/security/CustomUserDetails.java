package com.bookeatinglion.member.security;

import com.bookeatinglion.member.domain.Member;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security {@link UserDetails} 계약을 만족하는 {@link Member} 어댑터.
 *
 * <p>{@code JwtFilter}가 인증에 성공하면 이 객체가
 * {@code SecurityContext}의 인증 주체(principal)로 저장되며, 컨트롤러에서는
 * {@code @AuthenticationPrincipal CustomUserDetails}로 바로 주입받아
 * {@code memberId}/{@code username} 등을 꺼내 쓸 수 있다.</p>
 *
 * <p>베타 프로젝트의 {@code CustomUserDetails}와 동일한 구조이며, 이 프로젝트가
 * JPA 엔티티({@link Member})를 사용하는 점에 맞춰 개별 필드 대신 엔티티를 받는
 * 생성자를 추가로 제공한다.</p>
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long memberId;
    private final String username;
    private final String password;
    private final String role;
    private final String name;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * {@link Member} 엔티티로부터 인증 주체를 생성한다.
     *
     * @param member 인증에 성공한 회원 엔티티
     */
    public CustomUserDetails(Member member) {
        this.memberId = member.getId();
        this.username = member.getUsername();
        this.password = member.getPassword();
        this.role = member.getRole().name();
        this.name = member.getName();
        // Spring Security 표준 규약에 맞춰 "ROLE_" 접두사를 붙인 권한을 부여한다.
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + this.role));
    }

    /** 계정 만료 여부. 별도 만료 정책이 없으므로 항상 만료되지 않은 것으로 취급한다. */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 계정 잠금 여부. 별도 잠금 정책이 없으므로 항상 잠기지 않은 것으로 취급한다. */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 비밀번호(자격 증명) 만료 여부. 별도 정책이 없으므로 항상 만료되지 않은 것으로 취급한다. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 계정 활성화 여부. 별도 비활성화 정책이 없으므로 항상 활성 상태로 취급한다. */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
