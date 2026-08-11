package com.bookeatinglion.order.cart.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.exception.GlobalErrorHelper;
import com.bookeatinglion.order.cart.exception.CartDomainException;
import com.bookeatinglion.order.cart.exception.CartErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.order.cart.controller")
public class CartExceptionHandler {

    @ExceptionHandler(CartDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartDomainException(CartDomainException e) {
        CartErrorCode errorCode = e.getErrorCode();
        return GlobalErrorHelper.toResponse(errorCode.getStatus(), errorCode.name(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return GlobalErrorHelper.toValidationResponse(CartErrorCode.INVALID_REQUEST.name(), e);
    }
}
