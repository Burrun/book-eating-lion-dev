package com.bookeatinglion.member.card.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.member.card.exception.CardDomainException;
import com.bookeatinglion.member.card.exception.CardErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.member.card.controller")
public class CardExceptionHandler {

    @ExceptionHandler(CardDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleCardDomainException(CardDomainException e) {
        CardErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(CardErrorCode.INVALID_REQUEST.name(), message));
    }
}
