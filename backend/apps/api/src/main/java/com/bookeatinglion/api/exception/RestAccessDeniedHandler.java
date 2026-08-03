package com.bookeatinglion.api.exception;

import com.bookeatinglion.common.exception.ErrorCode;
import com.bookeatinglion.common.response.ApiResponse;
import com.bookeatinglion.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증은 되었으나 권한이 부족한 사용자가 API를 호출했을 때(예: USER가 관리자 API 호출)
 * Spring Security가 위임하는 핸들러.
 *
 * <p>{@link RestAuthenticationEntryPoint}와 마찬가지로 시큐리티 필터 체인 단계에서
 * 실행되므로 {@code GlobalExceptionHandler}가 아닌 이 클래스가 직접 응답을 작성하며,
 * 형식은 프로젝트 공통 엔벨로프({@link ApiResponse})와 동일하게 맞춘다.</p>
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * 권한 부족 시 403 응답을 공통 엔벨로프 형식으로 작성한다.
     *
     * @param request               권한이 거부된 원본 요청
     * @param response              클라이언트에 내려줄 응답
     * @param accessDeniedException Spring Security가 전달한 접근 거부 예외
     * @throws IOException 응답 바디를 작성하는 중 입출력 오류가 발생한 경우
     */
    @Override
    public void handle(HttpServletRequest request,
                        HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {

        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
        ApiResponse<Void> body = ApiResponse.error(new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage()));

        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
