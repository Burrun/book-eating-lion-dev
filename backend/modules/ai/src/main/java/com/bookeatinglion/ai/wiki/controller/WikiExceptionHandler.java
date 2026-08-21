package com.bookeatinglion.ai.wiki.controller;

import com.bookeatinglion.ai.wiki.exception.AiErrorCode;
import com.bookeatinglion.ai.wiki.exception.QuotaExceededException;
import com.bookeatinglion.common.dto.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.ai.wiki.controller")
public class WikiExceptionHandler {

    /** Retry-After 가 없으면 클라이언트가 즉시 재시도해서 초과 상태가 그대로 이어진다. */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuotaExceeded(QuotaExceededException e) {
        return ResponseEntity.status(AiErrorCode.QUOTA_EXCEEDED.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiResponse.error(AiErrorCode.QUOTA_EXCEEDED.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(AiErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.error(AiErrorCode.INVALID_REQUEST.name(), message));
    }
}
