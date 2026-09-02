package com.bookeatinglion.member.address.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.member.address.exception.AddressDomainException;
import com.bookeatinglion.member.address.exception.AddressErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * delivery 모듈이 쪼개지면서 DeliveryExceptionHandler 에서 갈라져 나왔다.
 * addresses 는 member_db 소속이므로 배송 예외와 같은 계층을 공유하지 않는다.
 */
@RestControllerAdvice(basePackages = "com.bookeatinglion.member.address.controller")
public class AddressExceptionHandler {

    @ExceptionHandler(AddressDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleAddressDomainException(AddressDomainException e) {
        AddressErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(AddressErrorCode.INVALID_REQUEST.name(), message));
    }
}
