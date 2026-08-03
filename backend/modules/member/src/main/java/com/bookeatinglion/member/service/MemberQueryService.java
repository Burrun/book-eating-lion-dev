package com.bookeatinglion.member.service;

import com.bookeatinglion.common.exception.BusinessException;
import com.bookeatinglion.common.exception.ErrorCode;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.dto.MemberDto;
import com.bookeatinglion.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원(Member) 도메인의 조회(Query) 유스케이스를 담당하는 서비스.
 *
 * <p>베타 프로젝트의 CQRS 스타일 서비스 분리 컨벤션을 따라, 데이터를 변경하지 않는
 * "내 정보 조회"/"등급 조회" 유스케이스를 이 클래스에 모은다. 프로필 수정처럼 데이터를
 * 변경하는 유스케이스는 {@link MemberCommandService}가 담당한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MemberRepository memberRepository;

    /**
     * {@code GET /api/members/me} 요청을 처리한다.
     *
     * @param username 인증된 사용자의 로그인 아이디(JWT subject)
     * @return 회원 프로필 응답 DTO
     * @throws BusinessException {@link ErrorCode#MEMBER_NOT_FOUND} - 토큰은 유효하지만 회원 레코드를 찾을 수 없는 경우
     */
    public MemberDto.MemberResponse getMe(String username) {
        Member member = findMemberOrThrow(username);
        return toMemberResponse(member);
    }

    /**
     * {@code GET /api/members/me/grade} 요청을 처리한다.
     *
     * @param username 인증된 사용자의 로그인 아이디(JWT subject)
     * @return 회원의 멤버십 등급과 보유 포인트를 담은 응답 DTO
     * @throws BusinessException {@link ErrorCode#MEMBER_NOT_FOUND} - 토큰은 유효하지만 회원 레코드를 찾을 수 없는 경우
     */
    public MemberDto.MemberGradeResponse getGrade(String username) {
        Member member = findMemberOrThrow(username);
        return MemberDto.MemberGradeResponse.builder()
                .grade(member.getGrade().name())
                .point(member.getPoint())
                .build();
    }

    /**
     * 아이디로 회원을 조회하고, 없으면 {@link BusinessException}을 던지는 공통 헬퍼.
     *
     * @param username 조회할 로그인 아이디
     * @return 조회된 회원 엔티티
     * @throws BusinessException {@link ErrorCode#MEMBER_NOT_FOUND} - 회원이 존재하지 않는 경우
     */
    private Member findMemberOrThrow(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * {@link Member} 엔티티를 {@link MemberDto.MemberResponse}로 변환한다.
     *
     * @param member 변환할 회원 엔티티
     * @return 변환된 응답 DTO
     */
    static MemberDto.MemberResponse toMemberResponse(Member member) {
        return MemberDto.MemberResponse.builder()
                .memberId(member.getId())
                .username(member.getUsername())
                .name(member.getName())
                .gender(member.getGender())
                .age(member.getAge())
                .role(member.getRole().name())
                .grade(member.getGrade().name())
                .point(member.getPoint())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
