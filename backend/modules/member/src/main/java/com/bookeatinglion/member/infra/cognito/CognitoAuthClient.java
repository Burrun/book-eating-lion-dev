package com.bookeatinglion.member.infra.cognito;

import com.bookeatinglion.member.config.CognitoProperties;
import com.bookeatinglion.member.exception.CognitoAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CognitoAuthClient {

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties properties;

    public String signUp(String email, String password, String name) {
        try {
            AdminCreateUserResponse createUserResponse = cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(properties.userPoolId())
                    .username(email)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build(),
                            AttributeType.builder().name("name").value(name).build()
                    )
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
                    .orElseThrow(() -> new CognitoAuthException("COGNITO_SIGNUP_FAILED", "Cognito 사용자 식별자(sub)를 확인할 수 없습니다."));
        } catch (InvalidPasswordException e) {
            throw new CognitoAuthException("INVALID_PASSWORD", resolvePasswordPolicyMessage(e.awsErrorDetails().errorMessage()), e);
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
            throw new CognitoAuthException("COGNITO_LOGIN_FAILED", e.awsErrorDetails().errorMessage(), e);
        }
    }

    public AuthenticationResultType refresh(String refreshToken) {
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
            throw new CognitoAuthException("COGNITO_REFRESH_FAILED", e.awsErrorDetails().errorMessage(), e);
        }
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
