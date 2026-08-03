package com.bookeatinglion.member.repository;

import com.bookeatinglion.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link Member} 엔티티에 대한 데이터 접근 계층.
 *
 * <p>로그인/인증 흐름에서는 이메일이 아니라 {@code username}이 식별자로 사용되므로,
 * Spring Data JPA의 쿼리 메서드 규약을 이용해 {@code username} 기반 조회 메서드를 제공한다.</p>
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 로그인 아이디(username)로 회원을 조회한다.
     *
     * @param username 조회할 로그인 아이디
     * @return 일치하는 회원이 있으면 {@link Member}를 담은 {@link Optional}, 없으면 빈 {@link Optional}
     */
    Optional<Member> findByUsername(String username);

    /**
     * 회원가입 시 아이디 중복 여부를 확인하기 위한 존재 여부 조회.
     *
     * @param username 중복 여부를 확인할 로그인 아이디
     * @return 이미 사용 중인 아이디면 {@code true}
     */
    boolean existsByUsername(String username);
}
