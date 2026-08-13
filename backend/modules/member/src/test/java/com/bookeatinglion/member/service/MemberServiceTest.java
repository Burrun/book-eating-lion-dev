package com.bookeatinglion.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookeatinglion.member.domain.Gender;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.dto.MemberResponse;
import com.bookeatinglion.member.dto.MemberUpdateRequest;
import com.bookeatinglion.member.exception.MemberNotFoundException;
import com.bookeatinglion.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void 내_프로필을_조회한다() {
        Member member = Member.register("sub-1", "lion@bookeating.com", "책먹는사자");
        when(memberRepository.findById("sub-1")).thenReturn(Optional.of(member));

        MemberResponse response = memberService.getMyProfile("sub-1");

        assertThat(response.email()).isEqualTo("lion@bookeating.com");
        assertThat(response.name()).isEqualTo("책먹는사자");
    }

    @Test
    void 존재하지_않는_회원을_조회하면_예외를_던진다() {
        when(memberRepository.findById("sub-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyProfile("sub-unknown")).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void 프로필을_부분_수정한다() {
        Member member = Member.register("sub-1", "lion@bookeating.com", "책먹는사자");
        when(memberRepository.findById("sub-1")).thenReturn(Optional.of(member));

        MemberResponse response = memberService.updateProfile(
                "sub-1", new MemberUpdateRequest("새이름", "010-1234-5678", Gender.MALE, LocalDate.of(2000, 1, 1)));

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(response.phoneNumber()).isEqualTo("010-1234-5678");
        assertThat(response.gender()).isEqualTo(Gender.MALE);
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    }
}
