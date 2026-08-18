package com.bookeatinglion.member.infra.cognito;

import com.bookeatinglion.member.config.CognitoProperties;
import com.bookeatinglion.member.exception.CognitoAuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CognitoAuthClient {

    // 로컬/테스트 환경 전용 목업 인증 우회. app.cognito.user-pool-id 가 비어있거나 "mock"일
    // 때만 켜진다 — AWS_COGNITO_USER_POOL_ID 를 실제로 설정하는 순간(local 포함) 이 분기는
    // 전혀 타지 않고 기존 AWS SDK 경로 그대로 동작한다. prod 는 issuer-uri/user-pool-id 에
    // 기본값을 두지 않으므로(기동 실패 유도, application-prod.yml 참고) 여기로 흘러들 수 없다.
    //
    // 회원가입/로그인 상태는 이 프로세스 안에서만 메모리로 유지한다(재시작 시 초기화) —
    // 실제 비밀번호를 members 테이블에 남기지 않기 위한 의도적 격리다.
    private static final BCryptPasswordEncoder MOCK_PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final SecretKey MOCK_SIGNING_KEY = Jwts.SIG.HS256.key().build();
    private static final Duration MOCK_ACCESS_TTL = Duration.ofHours(1);
    private static final Duration MOCK_REFRESH_TTL = Duration.ofDays(30);
    private static final String MOCK_ACCESS_TOKEN_USE = "access";
    private static final String MOCK_REFRESH_TOKEN_USE = "refresh";

    private final Map<String, MockUser> mockUsers = new ConcurrentHashMap<>();

    private record MockUser(String sub, String passwordHash) {}

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties properties;

    private boolean isMockMode() {
        String userPoolId = properties.userPoolId();
        return !StringUtils.hasText(userPoolId) || "mock".equalsIgnoreCase(userPoolId);
    }

    public String signUp(String email, String password, String name) {
        if (isMockMode()) {
            return mockSignUp(email, password);
        }
        try {
            AdminCreateUserResponse createUserResponse = cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(email)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder()
                                    .name("email_verified")
                                    .value("true")
                                    .build(),
                            AttributeType.builder().name("name").value(name).build())
                    .messageAction(MessageActionType.SUPPRESS)
                    .build());

            try {
                cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                        .userPoolId(properties.userPoolId())
                        .username(email)
                        .password(password)
                        .permanent(true)
                        .build());
            } catch (CognitoIdentityProviderException e) {
                rollbackCreatedUser(email);
                throw e;
            }

            return createUserResponse.user().attributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElseThrow(() ->
                            new CognitoAuthException("COGNITO_SIGNUP_FAILED", "Cognito 사용자 식별자(sub)를 확인할 수 없습니다."));
        } catch (InvalidPasswordException e) {
            throw new CognitoAuthException(
                    "INVALID_PASSWORD",
                    resolvePasswordPolicyMessage(e.awsErrorDetails().errorMessage()),
                    e);
        } catch (UsernameExistsException e) {
            throw new CognitoAuthException("DUPLICATE_EMAIL", "이미 가입된 이메일입니다.", e);
        } catch (CognitoIdentityProviderException e) {
            throw new CognitoAuthException("COGNITO_SIGNUP_FAILED", "회원가입 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
        }
    }

    // adminSetUserPassword 실패 시 방금 adminCreateUser 로 만든 사용자를 되돌린다.
    // 안 그러면 같은 이메일로는 UsernameExistsException 때문에 영원히 재가입할 수 없다.
    private void rollbackCreatedUser(String email) {
        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(email)
                    .build());
        } catch (Exception rollbackException) {
            log.error("Cognito 사용자 롤백 삭제 실패 - 수동 정리가 필요합니다. email={}", email, rollbackException);
        }
    }

    private String resolvePasswordPolicyMessage(String awsErrorMessage) {
        String lower = awsErrorMessage == null ? "" : awsErrorMessage.toLowerCase();
        if (lower.contains("uppercase")) {
            return "비밀번호에 대문자를 포함해주세요.";
        }
        if (lower.contains("lowercase")) {
            return "비밀번호에 소문자를 포함해주세요.";
        }
        if (lower.contains("numeric")) {
            return "비밀번호에 숫자를 포함해주세요.";
        }
        if (lower.contains("symbol")) {
            return "비밀번호에 특수문자를 포함해주세요.";
        }
        if (lower.contains("long") || lower.contains("short")) {
            return "비밀번호는 8자 이상이어야 합니다.";
        }
        return "회원가입 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    }

    public AuthenticationResultType login(String email, String password) {
        if (isMockMode()) {
            return mockLogin(email, password);
        }
        try {
            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .clientId(properties.clientId())
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(authParameters(email, password))
                    .build());
            return response.authenticationResult();
        } catch (NotAuthorizedException | UserNotFoundException e) {
            throw new CognitoAuthException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.", e);
        } catch (CognitoIdentityProviderException e) {
            throw new CognitoAuthException(
                    "COGNITO_LOGIN_FAILED", e.awsErrorDetails().errorMessage(), e);
        }
    }

    public AuthenticationResultType refresh(String refreshToken) {
        if (isMockMode()) {
            return mockRefresh(refreshToken);
        }
        try {
            Map<String, String> params = new HashMap<>();
            params.put("REFRESH_TOKEN", refreshToken);

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .clientId(properties.clientId())
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .authParameters(params)
                    .build());
            return response.authenticationResult();
        } catch (NotAuthorizedException e) {
            throw new CognitoAuthException("INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다.", e);
        } catch (CognitoIdentityProviderException e) {
            throw new CognitoAuthException(
                    "COGNITO_REFRESH_FAILED", e.awsErrorDetails().errorMessage(), e);
        }
    }

    /** 이메일 해시 기반 UUID를 sub 로 쓴다 — 같은 이메일이면 항상 같은 sub 가 나온다(재현 가능). */
    private String mockSignUp(String email, String password) {
        log.warn("[MOCK COGNITO] user-pool-id 미설정 — 실제 AWS Cognito 호출 없이 로컬 목업으로 가입 처리합니다. email={}", email);
        if (mockUsers.containsKey(email)) {
            throw new CognitoAuthException("DUPLICATE_EMAIL", "이미 가입된 이메일입니다.");
        }
        String sub =
                UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8)).toString();
        mockUsers.put(email, new MockUser(sub, MOCK_PASSWORD_ENCODER.encode(password)));
        return sub;
    }

    private AuthenticationResultType mockLogin(String email, String password) {
        log.warn("[MOCK COGNITO] user-pool-id 미설정 — 실제 AWS Cognito 호출 없이 로컬 목업으로 로그인 처리합니다. email={}", email);
        MockUser user = mockUsers.get(email);
        if (user == null || !MOCK_PASSWORD_ENCODER.matches(password, user.passwordHash())) {
            throw new CognitoAuthException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return mockAuthenticationResult(user.sub(), email, true);
    }

    private AuthenticationResultType mockRefresh(String refreshToken) {
        log.warn("[MOCK COGNITO] user-pool-id 미설정 — 실제 AWS Cognito 호출 없이 로컬 목업으로 토큰을 재발급합니다.");
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(MOCK_SIGNING_KEY)
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
            if (!MOCK_REFRESH_TOKEN_USE.equals(claims.get("token_use", String.class))) {
                throw new CognitoAuthException("INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다.");
            }
            // refresh token 은 재발급(회전)하지 않는다 — AuthService.toTokenResponse 가
            // result.refreshToken() 이 null 이면 요청에 쓰인 원본 토큰을 그대로 응답에 채운다.
            return mockAuthenticationResult(claims.getSubject(), claims.get("email", String.class), false);
        } catch (CognitoAuthException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new CognitoAuthException("INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다.", e);
        }
    }

    /**
     * 실제 Cognito JWT가 아니라 로컬 전용 키로 서명한 목업 토큰이다 — 이 서비스(member-api)
     * 자신의 로그인/재발급 응답 계약만 충족하며, Cognito JWK로 서명을 검증하는 다른
     * 서비스(catalog/order/ai-api)의 인증에는 쓸 수 없다. 여러 서비스를 통합 검증하려면
     * 그쪽에도 동일한 목업 검증 경로가 필요하다(이번 변경 범위 밖).
     */
    private AuthenticationResultType mockAuthenticationResult(String sub, String email, boolean includeRefreshToken) {
        Instant now = Instant.now();
        String accessToken = Jwts.builder()
                .subject(sub)
                .claim("email", email)
                .claim("token_use", MOCK_ACCESS_TOKEN_USE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(MOCK_ACCESS_TTL)))
                .signWith(MOCK_SIGNING_KEY)
                .compact();

        var builder = AuthenticationResultType.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn((int) MOCK_ACCESS_TTL.toSeconds());

        if (includeRefreshToken) {
            String refreshToken = Jwts.builder()
                    .subject(sub)
                    .claim("email", email)
                    .claim("token_use", MOCK_REFRESH_TOKEN_USE)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(MOCK_REFRESH_TTL)))
                    .signWith(MOCK_SIGNING_KEY)
                    .compact();
            builder.refreshToken(refreshToken);
        }

        return builder.build();
    }

    private Map<String, String> authParameters(String email, String password) {
        Map<String, String> params = new HashMap<>();
        params.put("USERNAME", email);
        params.put("PASSWORD", password);
        if (StringUtils.hasText(properties.clientSecret())) {
            params.put("SECRET_HASH", calculateSecretHash(email));
        }
        return params;
    }

    private String calculateSecretHash(String username) {
        try {
            String message = username + properties.clientId();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new CognitoAuthException("COGNITO_CONFIG_ERROR", "SECRET_HASH 계산에 실패했습니다.", e);
        }
    }
}
