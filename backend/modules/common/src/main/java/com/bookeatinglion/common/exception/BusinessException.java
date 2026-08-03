package com.bookeatinglion.common.exception;

import lombok.Getter;

/**
 * 예상 가능한 비즈니스 규칙 위반을 표현하는 공통 런타임 예외.
 *
 * <p>서비스 계층에서는 표준 {@link IllegalArgumentException} 등을 직접 던지는 대신
 * 이 예외에 {@link ErrorCode}를 담아 던진다. {@link GlobalExceptionHandler}가 이를 잡아
 * {@link ErrorCode}에 정의된 HTTP 상태와 메시지로 일관된 에러 응답을 만들어준다.</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 이 예외가 나타내는 에러의 종류. */
    private final ErrorCode errorCode;

    /**
     * {@link ErrorCode}의 기본 메시지를 그대로 사용하는 예외를 생성한다.
     *
     * @param errorCode 발생한 에러의 종류
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 기본 메시지 대신 상황에 맞는 커스텀 메시지를 사용하는 예외를 생성한다.
     *
     * @param errorCode 발생한 에러의 종류
     * @param message   클라이언트에게 노출할 커스텀 메시지
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
