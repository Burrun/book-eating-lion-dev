package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.exception.BookErrorCode;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.CatalogConflictException;
import com.bookeatinglion.book.exception.CategoryNotFoundException;
import com.bookeatinglion.book.exception.EbookAccessUnavailableException;
import com.bookeatinglion.book.exception.FaqNotFoundException;
import com.bookeatinglion.book.exception.InquiryAccessDeniedException;
import com.bookeatinglion.book.exception.InquiryNotFoundException;
import com.bookeatinglion.book.exception.InvalidRecommendationReactionException;
import com.bookeatinglion.book.exception.RestockAlertConflictException;
import com.bookeatinglion.book.exception.RestockAlertNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.exception.ReviewPermissionRequiredException;
import com.bookeatinglion.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.book.controller")
public class BookExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookNotFound(BookNotFoundException e) {
        return ResponseEntity.status(BookErrorCode.BOOK_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BookErrorCode.BOOK_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCategoryNotFound(CategoryNotFoundException e) {
        return ResponseEntity.status(BookErrorCode.CATEGORY_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BookErrorCode.CATEGORY_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(CatalogConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleCatalogConflict(CatalogConflictException e) {
        return ResponseEntity.status(BookErrorCode.CATALOG_CONFLICT.getStatus())
                .body(ApiResponse.error(BookErrorCode.CATALOG_CONFLICT.name(), e.getMessage()));
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

    @ExceptionHandler(RestockAlertNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestockAlertNotFound(RestockAlertNotFoundException e) {
        return ResponseEntity.status(BookErrorCode.RESTOCK_ALERT_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BookErrorCode.RESTOCK_ALERT_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(RestockAlertConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestockAlertConflict(RestockAlertConflictException e) {
        return ResponseEntity.status(BookErrorCode.RESTOCK_ALERT_CONFLICT.getStatus())
                .body(ApiResponse.error(BookErrorCode.RESTOCK_ALERT_CONFLICT.name(), e.getMessage()));
    }

    @ExceptionHandler(InquiryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInquiryNotFound(InquiryNotFoundException e) {
        return ResponseEntity.status(BookErrorCode.INQUIRY_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BookErrorCode.INQUIRY_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(InquiryAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleInquiryAccessDenied(InquiryAccessDeniedException e) {
        return ResponseEntity.status(BookErrorCode.INQUIRY_ACCESS_DENIED.getStatus())
                .body(ApiResponse.error(BookErrorCode.INQUIRY_ACCESS_DENIED.name(), e.getMessage()));
    }

    @ExceptionHandler(FaqNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFaqNotFound(FaqNotFoundException e) {
        return ResponseEntity.status(BookErrorCode.FAQ_NOT_FOUND.getStatus())
                .body(ApiResponse.error(BookErrorCode.FAQ_NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(InvalidRecommendationReactionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRecommendationReaction(
            InvalidRecommendationReactionException e) {
        return ResponseEntity.status(BookErrorCode.INVALID_RECOMMENDATION_REACTION.getStatus())
                .body(ApiResponse.error(BookErrorCode.INVALID_RECOMMENDATION_REACTION.name(), e.getMessage()));
    }

    @ExceptionHandler(EbookAccessUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleEbookAccessUnavailable(EbookAccessUnavailableException e) {
        return ResponseEntity.status(BookErrorCode.EBOOK_ACCESS_UNAVAILABLE.getStatus())
                .body(ApiResponse.error(BookErrorCode.EBOOK_ACCESS_UNAVAILABLE.name(), e.getMessage()));
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
