package com.bookeatinglion.member.service;

import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.dto.LoginRequest;
import com.bookeatinglion.member.dto.RefreshRequest;
import com.bookeatinglion.member.dto.SignupRequest;
import com.bookeatinglion.member.dto.SignupResponse;
import com.bookeatinglion.member.dto.TokenResponse;
import com.bookeatinglion.member.exception.DuplicateEmailException;
import com.bookeatinglion.member.infra.cognito.CognitoAuthClient;
import com.bookeatinglion.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
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

        String cognitoSub = cognitoAuthClient.signUp(request.email(), request.password(), request.name());
        Member member = memberRepository.save(Member.register(cognitoSub, request.email(), request.name()));
        return SignupResponse.from(member);
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
