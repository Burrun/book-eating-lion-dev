package com.bookeatinglion.order.coupon.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.exception.GlobalErrorHelper;
import com.bookeatinglion.order.coupon.exception.CouponDomainException;
import com.bookeatinglion.order.coupon.exception.CouponErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.order.coupon.controller")
public class CouponExceptionHandler {

    @ExceptionHandler(CouponDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponDomainException(CouponDomainException e) {
        CouponErrorCode errorCode = e.getErrorCode();
        return GlobalErrorHelper.toResponse(errorCode.getStatus(), errorCode.name(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return GlobalErrorHelper.toValidationResponse(CouponErrorCode.INVALID_REQUEST.name(), e);
    }
}
