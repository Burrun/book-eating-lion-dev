package com.bookeatinglion.member.service;

import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.dto.LoginRequest;
import com.bookeatinglion.member.dto.RefreshRequest;
import com.bookeatinglion.member.dto.SignupRequest;
import com.bookeatinglion.member.dto.SignupResponse;
import com.bookeatinglion.member.dto.TokenResponse;
import com.bookeatinglion.member.exception.DuplicateEmailException;
import com.bookeatinglion.member.exception.DuplicateNicknameException;
import com.bookeatinglion.member.infra.cognito.CognitoAuthClient;
import com.bookeatinglion.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final CognitoAuthClient cognitoAuthClient;
    private final MemberRepository memberRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new DuplicateNicknameException(request.nickname());
        }

        String cognitoSub = cognitoAuthClient.signUp(request.email(), request.password(), request.name());
        try {
            Member member = memberRepository.save(
                    Member.register(cognitoSub, request.email(), request.name(), request.nickname()));
            return SignupResponse.from(member);
        } catch (DataIntegrityViolationException e) {
            // 위 existsBy 체크와 이 save 사이의 동시 가입 레이스만 여기로 떨어진다 - 어느 쪽
            // UNIQUE 제약을 건드렸는지 제약 이름(uk_members_nickname/uk_members_email,
            // db/postgres/01-member_db.sql)으로 구분한다.
            Throwable rootCause = e.getMostSpecificCause();
            if (rootCause != null
                    && rootCause.getMessage() != null
                    && rootCause.getMessage().contains("nickname")) {
                throw new DuplicateNicknameException(request.nickname());
            }
            throw new DuplicateEmailException(request.email());
        }
    }

    public TokenResponse login(LoginRequest request) {
        AuthenticationResultType result = cognitoAuthClient.login(request.email(), request.password());
        return toTokenResponse(result, null);
    }

    public TokenResponse refresh(RefreshRequest request) {
        AuthenticationResultType result = cognitoAuthClient.refresh(request.refreshToken());
        return toTokenResponse(result, request.refreshToken());
    }

    private TokenResponse toTokenResponse(AuthenticationResultType result, String fallbackRefreshToken) {
        String refreshToken = result.refreshToken() != null ? result.refreshToken() : fallbackRefreshToken;
        return new TokenResponse(result.accessToken(), refreshToken, result.tokenType(), result.expiresIn());
    }
}
