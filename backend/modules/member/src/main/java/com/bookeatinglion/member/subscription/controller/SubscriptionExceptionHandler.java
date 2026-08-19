package com.bookeatinglion.member.subscription.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.member.subscription.exception.SubscriptionDomainException;
import com.bookeatinglion.member.subscription.exception.SubscriptionErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.member.subscription.controller")
public class SubscriptionExceptionHandler {

    @ExceptionHandler(SubscriptionDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleSubscriptionDomainException(SubscriptionDomainException e) {
        SubscriptionErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(SubscriptionErrorCode.INVALID_REQUEST.name(), message));
    }
}
