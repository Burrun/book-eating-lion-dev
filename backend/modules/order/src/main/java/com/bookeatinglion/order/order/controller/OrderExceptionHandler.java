package com.bookeatinglion.order.order.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.exception.GlobalErrorHelper;
import com.bookeatinglion.order.order.exception.OrderDomainException;
import com.bookeatinglion.order.order.exception.OrderErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * OrderDomainException 단일 기반형이 order/payment 예외를 전부 포괄한다 — 물리적으로는
 * order.exception/payment.exception/lock 세 패키지에 나뉘어 있어도, 전부 OrderController 나
 * PaymentController 가 처리하는 요청에서만 던져지므로 이 하나의 핸들러로 충분하다.
 */
@RestControllerAdvice(
        basePackages = {"com.bookeatinglion.order.order.controller", "com.bookeatinglion.order.payment.controller"})
public class OrderExceptionHandler {

    @ExceptionHandler(OrderDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderDomainException(OrderDomainException e) {
        OrderErrorCode errorCode = e.getErrorCode();
        return GlobalErrorHelper.toResponse(errorCode.getStatus(), errorCode.name(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return GlobalErrorHelper.toValidationResponse(OrderErrorCode.INVALID_REQUEST.name(), e);
    }
}
