package com.bookeatinglion.member.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.member.exception.CognitoAuthException;
import com.bookeatinglion.member.exception.DuplicateEmailException;
import com.bookeatinglion.member.exception.DuplicateNicknameException;
import com.bookeatinglion.member.exception.MemberNotFoundException;
import com.bookeatinglion.member.exception.UnauthorizedException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.member.controller")
public class MemberExceptionHandler {

    private static final Set<String> UNAUTHORIZED_CODES = Set.of("INVALID_CREDENTIALS", "INVALID_REFRESH_TOKEN");

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMemberNotFound(MemberNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateNickname(DuplicateNicknameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(CognitoAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleCognitoAuth(CognitoAuthException e) {
        HttpStatus status = resolveStatus(e.getCode());
        return ResponseEntity.status(status).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("INVALID_REQUEST", message));
    }

    private HttpStatus resolveStatus(String code) {
        if (UNAUTHORIZED_CODES.contains(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if ("DUPLICATE_EMAIL".equals(code)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
