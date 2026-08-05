package com.bookeatinglion.admin.controller;

import com.bookeatinglion.admin.exception.AdminErrorCode;
import com.bookeatinglion.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.bookeatinglion.admin.controller")
public class AdminExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "잘못된 요청 파라미터입니다: " + e.getName();
        return ResponseEntity.status(AdminErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.error(AdminErrorCode.INVALID_REQUEST.name(), message));
    }
}
