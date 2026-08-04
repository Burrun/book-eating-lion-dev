package com.bookeatinglion.member.service;

import com.bookeatinglion.member.dto.LoginRequest;
import com.bookeatinglion.member.dto.RefreshRequest;
import com.bookeatinglion.member.dto.SignupRequest;
import com.bookeatinglion.member.dto.SignupResponse;
import com.bookeatinglion.member.dto.TokenResponse;
import com.bookeatinglion.member.exception.CognitoAuthException;
import com.bookeatinglion.member.exception.DuplicateEmailException;
import com.bookeatinglion.member.infra.cognito.CognitoAuthClient;
import com.bookeatinglion.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CognitoAuthClient cognitoAuthClient;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void 회원가입시_Cognito에_등록하고_DB에_동기화한다() {
        when(memberRepository.existsByEmail("lion@bookeating.com")).thenReturn(false);
        when(cognitoAuthClient.signUp("lion@bookeating.com", "password1234", "책먹는사자")).thenReturn("sub-1");
        when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = authService.signup(
                new SignupRequest("lion@bookeating.com", "password1234", "책먹는사자"));

        assertThat(response.email()).isEqualTo("lion@bookeating.com");
        assertThat(response.name()).isEqualTo("책먹는사자");
    }

    @Test
    void 이미_가입된_이메일이면_Cognito_호출없이_예외를_던진다() {
        when(memberRepository.existsByEmail("lion@bookeating.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("lion@bookeating.com", "password1234", "책먹는사자")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(cognitoAuthClient, never()).signUp(any(), any(), any());
    }

    @Test
    void 회원가입시_Cognito_인증에_실패하면_예외를_전파하고_DB에_저장하지_않는다() {
        when(memberRepository.existsByEmail("lion@bookeating.com")).thenReturn(false);
        when(cognitoAuthClient.signUp("lion@bookeating.com", "password1234", "책먹는사자"))
                .thenThrow(new CognitoAuthException("COGNITO_SIGNUP_FAILED", "Cognito 가입에 실패했습니다."));

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("lion@bookeating.com", "password1234", "책먹는사자")))
                .isInstanceOf(CognitoAuthException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 로그인시_토큰을_발급받는다() {
        AuthenticationResultType result = AuthenticationResultType.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();
        when(cognitoAuthClient.login("lion@bookeating.com", "password1234")).thenReturn(result);

        TokenResponse response = authService.login(new LoginRequest("lion@bookeating.com", "password1234"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void 토큰_재발급시_새_refreshToken이_없으면_기존_토큰을_유지한다() {
        AuthenticationResultType result = AuthenticationResultType.builder()
                .accessToken("new-access-token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();
        when(cognitoAuthClient.refresh("old-refresh-token")).thenReturn(result);

        TokenResponse response = authService.refresh(new RefreshRequest("old-refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("old-refresh-token");
    }
}
