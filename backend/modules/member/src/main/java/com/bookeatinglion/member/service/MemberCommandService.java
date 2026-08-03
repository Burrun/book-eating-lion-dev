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
 * 회원(Member) 도메인의 상태 변경(Command) 유스케이스를 담당하는 서비스.
 *
 * <p>현재는 프로필 부분 수정만 존재하지만, 향후 비밀번호 변경/회원 탈퇴 등
 * 회원 데이터를 변경하는 유스케이스가 추가되면 이 클래스에 모은다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService {

    private final MemberRepository memberRepository;

    /**
     * {@code PATCH /api/members/me} 요청을 처리한다.
     *
     * <p>영속성 컨텍스트에서 조회한 엔티티의 필드를 변경하면 트랜잭션 종료 시점에
     * 변경 감지(Dirty Checking)로 자동 반영되므로, 별도의 {@code save} 호출이 필요 없다.</p>
     *
     * @param username 인증된 사용자의 로그인 아이디(JWT subject)
     * @param request  수정할 필드만 채워진 부분 업데이트 요청(null 필드는 기존 값 유지)
     * @return 수정이 반영된 최신 회원 프로필 응답 DTO
     * @throws BusinessException {@link ErrorCode#MEMBER_NOT_FOUND} - 토큰은 유효하지만 회원 레코드를 찾을 수 없는 경우
     */
    public MemberDto.MemberResponse updateMe(String username, MemberDto.MemberUpdateRequest request) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        member.updateProfile(request.getName(), request.getGender(), request.getAge());

        return MemberQueryService.toMemberResponse(member);
    }
}
