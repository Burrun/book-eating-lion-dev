package com.bookeatinglion.order.delivery.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.delivery.exception.DeliveryDomainException;
import com.bookeatinglion.order.delivery.exception.DeliveryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.order.delivery.controller")
public class DeliveryExceptionHandler {

    @ExceptionHandler(DeliveryDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryDomainException(DeliveryDomainException e) {
        DeliveryErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(DeliveryErrorCode.INVALID_REQUEST.name(), message));
    }
}
