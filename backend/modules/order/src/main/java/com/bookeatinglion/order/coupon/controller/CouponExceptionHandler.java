package com.bookeatinglion.order.coupon.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.coupon.exception.CouponDomainException;
import com.bookeatinglion.order.coupon.exception.CouponErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.order.coupon.controller")
public class CouponExceptionHandler {

    @ExceptionHandler(CouponDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponDomainException(CouponDomainException e) {
        CouponErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(CouponErrorCode.INVALID_REQUEST.name(), message));
    }
}
