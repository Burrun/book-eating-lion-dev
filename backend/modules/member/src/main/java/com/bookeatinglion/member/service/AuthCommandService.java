package com.bookeatinglion.member.service;

import com.bookeatinglion.common.exception.BusinessException;
import com.bookeatinglion.common.exception.ErrorCode;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.domain.MemberGrade;
import com.bookeatinglion.member.domain.Role;
import com.bookeatinglion.member.dto.AuthDto;
import com.bookeatinglion.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증(Auth) 도메인의 상태 변경(Command) 유스케이스를 담당하는 서비스.
 *
 * <p>베타 프로젝트의 {@code AuthCommandService}/{@code AuthQueryService} 분리 컨벤션을 따라,
 * 데이터를 변경하는 회원가입 로직만 이 클래스에 두고, 조회 위주의 로그인/토큰 재발급은
 * {@link AuthQueryService}가 담당한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthCommandService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * {@code POST /api/auth/signup} 요청을 처리한다.
     *
     * <p>아이디 중복 여부를 확인한 뒤, 비밀번호를 {@link PasswordEncoder}로 암호화하여 저장하고,
     * 신규 회원에게는 항상 기본 권한({@link Role#USER})과 기본 등급({@link MemberGrade#BASIC}),
     * 0포인트를 부여한다.</p>
     *
     * @param request 회원가입 요청 바디(아이디/비밀번호/이름/성별/나이)
     * @return 생성된 회원의 식별자와 아이디
     * @throws BusinessException {@link ErrorCode#DUPLICATE_USERNAME} - 이미 사용 중인 아이디로 가입을 시도한 경우
     */
    public AuthDto.SignupResponse signup(AuthDto.SignupRequest request) {
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        Member member = Member.builder()
                .username(request.getUsername())
                // 평문 비밀번호를 절대 저장하지 않고 반드시 인코딩을 거친다.
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .gender(request.getGender())
                .age(request.getAge())
                .role(Role.USER)
                .grade(MemberGrade.BASIC)
                .point(0L)
                .build();

        Member saved = memberRepository.save(member);

        return AuthDto.SignupResponse.builder()
                .memberId(saved.getId())
                .username(saved.getUsername())
                .build();
    }
}
