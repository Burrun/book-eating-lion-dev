package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.exception.BookErrorCode;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.exception.ReviewPermissionRequiredException;
import com.bookeatinglion.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice(basePackages = "com.bookeatinglion.book.controller")
public class BookExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookNotFound(BookNotFoundException e) {
        return ResponseEntity.status(BookErrorCode.BOOK_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BookErrorCode.BOOK_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewNotFound(ReviewNotFoundException e) {
        return ResponseEntity.status(BookErrorCode.REVIEW_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BookErrorCode.REVIEW_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(ReviewAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewAccessDenied(ReviewAccessDeniedException e) {
        return ResponseEntity.status(BookErrorCode.REVIEW_ACCESS_DENIED.getStatus())
                .body(ApiResponse.error(BookErrorCode.REVIEW_ACCESS_DENIED.name(), e.getMessage()));
    }

    @ExceptionHandler(ReviewPermissionRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewPermissionRequired(ReviewPermissionRequiredException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(BookErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.error(BookErrorCode.INVALID_REQUEST.name(), message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(BookErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.error(BookErrorCode.INVALID_REQUEST.name(), e.getMessage()));
    }
}
