package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Gender;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 인증(Auth) 도메인에서 사용하는 요청/응답 DTO 모음.
 *
 * <p>베타 프로젝트({@code book-eating-lion-beta})의 {@code AuthDto} 컨벤션을 그대로 따라,
 * 서로 관련 있는 DTO들을 개별 파일로 분산시키지 않고 하나의 클래스 안에
 * {@code static nested class}로 묶어 관리한다. 필드명은 요구사항에 따라 모두
 * camelCase로 작성한다.</p>
 */
public class AuthDto {

    private AuthDto() {
        // 네임스페이스 역할만 하는 클래스이므로 인스턴스화하지 않는다.
    }

    /** {@code POST /api/auth/signup} 요청 바디. */
    @Getter
    @Setter
    public static class SignupRequest {

        @NotBlank(message = "아이디는 필수입니다.")
        private String username;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;

        @NotBlank(message = "이름은 필수입니다.")
        private String name;

        @NotNull(message = "성별은 필수입니다.")
        private Gender gender;

        @NotNull(message = "나이는 필수입니다.")
        @Min(value = 1, message = "나이는 1살 이상이어야 합니다.")
        private Integer age;
    }

    /** {@code POST /api/auth/signup} 성공 응답 바디. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class SignupResponse {
        private Long memberId;
        private String username;
    }

    /** {@code POST /api/auth/login} 요청 바디. */
    @Getter
    @Setter
    public static class LoginRequest {

        @NotBlank(message = "아이디는 필수입니다.")
        private String username;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;
    }

    /** {@code POST /api/auth/refresh} 요청 바디. */
    @Getter
    @Setter
    public static class RefreshRequest {

        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        private String refreshToken;
    }

    /**
     * {@code POST /api/auth/login}, {@code POST /api/auth/refresh}의 공통 성공 응답 바디.
     * Access/Refresh Token 한 쌍과 클라이언트가 즉시 사용할 수 있는 회원 요약 정보를 함께 내려준다.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long memberId;
        private String username;
        private String role;
    }
}
