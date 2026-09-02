package com.bookeatinglion.member.service;

import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.dto.MemberResponse;
import com.bookeatinglion.member.dto.MemberUpdateRequest;
import com.bookeatinglion.member.dto.NotificationProfileResponse;
import com.bookeatinglion.member.exception.MemberNotFoundException;
import com.bookeatinglion.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberResponse getMyProfile(String memberId) {
        return MemberResponse.from(getMember(memberId));
    }

    @Transactional
    public MemberResponse updateProfile(String memberId, MemberUpdateRequest request) {
        Member member = getMember(memberId);
        member.updateProfile(request.name(), request.phoneNumber(), request.gender(), request.birthDate());
        return MemberResponse.from(member);
    }

    public NotificationProfileResponse getNotificationProfile(String memberId) {
        return NotificationProfileResponse.from(getMember(memberId));
    }

    private Member getMember(String memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
