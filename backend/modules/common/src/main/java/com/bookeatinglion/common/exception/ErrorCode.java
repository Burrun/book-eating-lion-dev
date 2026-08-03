package com.bookeatinglion.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서비스 전역에서 발생 가능한 비즈니스 에러를 코드화한 enum.
 *
 * <p>각 상수는 {@link BusinessException}과 함께 던져지며,
 * {@link GlobalExceptionHandler}가 이 enum에 정의된 {@link HttpStatus}와
 * 기본 메시지를 이용해 {@link com.bookeatinglion.common.response.ApiResponse} 에러 응답을 만든다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /** 회원가입 시 이미 사용 중인 아이디로 가입을 시도한 경우. */
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),

    /** 로그인 시 아이디/비밀번호가 일치하지 않는 경우. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),

    /** 토큰의 subject(username)에 해당하는 회원을 DB에서 찾을 수 없는 경우. */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    /** 리프레시 토큰이 만료/변조되었거나 형식(type 클레임)이 올바르지 않은 경우. */
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 리프레시 토큰입니다."),

    /** {@code @Valid} 검증 실패 등 요청 값 자체가 유효하지 않은 경우. */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),

    /** 인증 정보(Access Token)가 없거나 유효하지 않아 인증이 필요한 경우. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    /** 인증은 되었으나 요청한 리소스에 대한 권한이 없는 경우. */
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    /** 위에서 분류되지 않은 예기치 못한 서버 내부 오류. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    /** 이 에러가 응답될 때 사용할 HTTP 상태 코드. */
    private final HttpStatus status;

    /** 클라이언트에 노출할 기본 에러 메시지. */
    private final String defaultMessage;
}
