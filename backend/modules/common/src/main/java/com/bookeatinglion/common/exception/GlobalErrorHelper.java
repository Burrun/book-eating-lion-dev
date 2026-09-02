package com.bookeatinglion.common.exception;

import com.bookeatinglion.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * 각 기능별 *ExceptionHandler(CartExceptionHandler, CouponExceptionHandler, OrderExceptionHandler 등)가
 * 똑같이 반복하던 두 가지 — 검증 실패 메시지 추출, ApiResponse 를 담은 ResponseEntity 생성 —
 * 를 한 곳으로 모았다. 도메인 예외를 어떤 상태코드/에러코드로 매핑할지는 여전히 각 핸들러의
 * *ErrorCode enum 이 결정한다 — 이 클래스는 그 결과를 응답으로 조립하는 배관만 맡는다.
 */
public final class GlobalErrorHelper {

    private GlobalErrorHelper() {}

    public static ResponseEntity<ApiResponse<Void>> toResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }

    public static ResponseEntity<ApiResponse<Void>> toValidationResponse(
            String code, MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return toResponse(HttpStatus.BAD_REQUEST, code, message);
    }
}
