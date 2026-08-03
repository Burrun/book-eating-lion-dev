package com.bookeatinglion.common.response;

import lombok.Getter;

/**
 * {@link ApiResponse}의 {@code error} 필드에 담기는 에러 상세 정보.
 *
 * <p>클라이언트가 에러 종류를 프로그래밍적으로 분기 처리할 수 있도록
 * 사람이 읽을 수 있는 {@code message}뿐 아니라 고정된 {@code code}도 함께 제공한다.</p>
 */
@Getter
public class ErrorResponse {

    /** {@link com.bookeatinglion.common.exception.ErrorCode}의 enum 이름(예: "INVALID_CREDENTIALS"). */
    private final String code;

    /** 사용자/개발자에게 노출할 에러 메시지. */
    private final String message;

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
