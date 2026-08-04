package com.bookeatinglion.usedbook.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.usedbook.exception.UsedBookErrorCode;
import com.bookeatinglion.usedbook.exception.UsedBookException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.usedbook.controller")
public class UsedBookExceptionHandler {

    @ExceptionHandler(UsedBookException.class)
    public ResponseEntity<ApiResponse<Void>> handleUsedBookException(UsedBookException e) {
        UsedBookErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(UsedBookErrorCode.INVALID_REQUEST.name(), message));
    }
}
