package com.bookeatinglion.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.domain.Member;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.save(Member.register("sub-1", "lion@bookeating.com", "책먹는사자"));
    }

    @Test
    void id로_회원을_조회한다() {
        Optional<Member> result = memberRepository.findById("sub-1");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("lion@bookeating.com");
    }

    @Test
    void 존재하지_않는_id는_빈_값을_반환한다() {
        Optional<Member> result = memberRepository.findById("sub-unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void email로_회원을_조회한다() {
        Optional<Member> result = memberRepository.findByEmail("lion@bookeating.com");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("sub-1");
    }

    @Test
    void 이미_가입된_email이면_true를_반환한다() {
        assertThat(memberRepository.existsByEmail("lion@bookeating.com")).isTrue();
        assertThat(memberRepository.existsByEmail("unknown@bookeating.com")).isFalse();
    }
}
