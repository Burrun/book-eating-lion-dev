package com.bookeatinglion.order.order.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.order.order.exception.OrderDomainException;
import com.bookeatinglion.order.order.exception.OrderErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * OrderDomainException 단일 기반형이 order/payment 예외를 전부 포괄한다 — 물리적으로는
 * order.exception/payment.exception/lock 세 패키지에 나뉘어 있어도, 전부 OrderController 가
 * 처리하는 요청에서만 던져지므로 이 하나의 핸들러로 충분하다.
 */
@RestControllerAdvice(basePackages = "com.bookeatinglion.order.order.controller")
public class OrderExceptionHandler {

    @ExceptionHandler(OrderDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderDomainException(OrderDomainException e) {
        OrderErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(OrderErrorCode.INVALID_REQUEST.name(), message));
    }
}
