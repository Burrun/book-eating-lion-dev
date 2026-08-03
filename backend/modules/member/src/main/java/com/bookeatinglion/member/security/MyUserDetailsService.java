package com.bookeatinglion.member.security;

import com.bookeatinglion.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security가 인증/인가 과정에서 사용자 정보를 조회할 때 사용하는
 * {@link UserDetailsService} 구현체.
 *
 * <p>{@code JwtFilter}는 Access Token에 담긴 subject(username)로 이 서비스를 호출해
 * 매 요청마다 DB에서 최신 회원 권한 정보를 읽어온다. 이렇게 하면 토큰 발급 이후
 * 관리자가 권한을 바꾸더라도(예: USER -&gt; ADMIN) 토큰을 재발급받는 즉시 반영된다.</p>
 */
@Service
@RequiredArgsConstructor
public class MyUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * 아이디로 회원을 조회하여 {@link CustomUserDetails}로 변환한다.
     *
     * @param username 조회할 로그인 아이디
     * @return 조회된 회원 정보를 감싼 {@link CustomUserDetails}
     * @throws UsernameNotFoundException 아이디에 해당하는 회원이 존재하지 않는 경우
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByUsername(username)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
    }
}
