package com.bookeatinglion.common.exception;

import com.bookeatinglion.common.response.ApiResponse;
import com.bookeatinglion.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 컨트롤러(및 그 하위 계층)에서 발생한 예외를 가로채 일관된
 * {@link ApiResponse} 엔벨로프 형식으로 변환해주는 전역 예외 처리기.
 *
 * <p>베타 프로젝트({@code book-eating-lion-beta})의
 * {@code GlobalExceptionHandler} 구조(=핸들러를 예외 타입별로 나열)를 그대로 따르되,
 * 반환 바디는 이 프로젝트의 공통 엔벨로프 포맷({@code success/data/error})에 맞춘다.</p>
 *
 * <p>참고: Spring Security의 {@code AuthenticationException}/{@code AccessDeniedException}은
 * 시큐리티 필터 체인 단계에서 발생하여 DispatcherServlet까지 도달하지 않으므로
 * 여기서 처리되지 않는다. 해당 예외들은
 * {@code RestAuthenticationEntryPoint}/{@code RestAccessDeniedHandler}가 담당한다.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스 계층에서 의도적으로 던진 {@link BusinessException}을 처리한다.
     * 예외가 담고 있는 {@link ErrorCode}의 HTTP 상태와 메시지를 그대로 사용한다.
     *
     * @param ex 발생한 비즈니스 예외
     * @return {@link ErrorCode}에 정의된 상태 코드와 에러 바디를 담은 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponse errorResponse = new ErrorResponse(errorCode.name(), ex.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorResponse));
    }

    /**
     * {@code @Valid} 검증에 실패한 요청(DTO의 Bean Validation 위반)을 처리한다.
     * 필드별 에러 메시지를 하나의 문자열로 합쳐 클라이언트가 원인을 파악할 수 있도록 한다.
     *
     * @param ex Bean Validation 실패 시 Spring MVC가 던지는 예외
     * @return HTTP 400과 함께 필드별 검증 실패 사유를 담은 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse errorResponse = new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), message);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(ApiResponse.error(errorResponse));
    }

    /**
     * 위에서 명시적으로 처리하지 않은 모든 예외에 대한 최종 방어선(fallback).
     * 예외 스택트레이스는 서버 로그에만 남기고, 클라이언트에는 내부 구현이 드러나지 않도록
     * {@link ErrorCode#INTERNAL_ERROR}의 일반적인 메시지만 반환한다.
     *
     * @param ex 처리되지 않은 예외
     * @return HTTP 500과 함께 일반화된 에러 메시지를 담은 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception occurred", ex);
        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(errorResponse));
    }
}
