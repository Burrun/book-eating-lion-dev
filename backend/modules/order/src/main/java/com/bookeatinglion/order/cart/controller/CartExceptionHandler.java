package com.bookeatinglion.order.cart.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.cart.exception.CartDomainException;
import com.bookeatinglion.order.cart.exception.CartErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.order.cart.controller")
public class CartExceptionHandler {

    @ExceptionHandler(CartDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartDomainException(CartDomainException e) {
        CartErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(CartErrorCode.INVALID_REQUEST.name(), message));
    }
}
