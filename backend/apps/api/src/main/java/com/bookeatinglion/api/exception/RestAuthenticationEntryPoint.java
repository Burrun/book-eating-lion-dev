package com.bookeatinglion.api.exception;

import com.bookeatinglion.common.exception.ErrorCode;
import com.bookeatinglion.common.response.ApiResponse;
import com.bookeatinglion.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증되지 않은 사용자가 인증이 필요한 API를 호출했을 때 Spring Security가 위임하는 진입점.
 *
 * <p>이 클래스는 Spring MVC의 {@code DispatcherServlet}에 도달하기 전, 시큐리티 필터 체인
 * 단계에서 실행되기 때문에 {@code GlobalExceptionHandler}(@RestControllerAdvice)로는
 * 처리할 수 없다. 베타 프로젝트도 동일한 이유로 별도의
 * {@code RestAuthenticationEntryPoint}를 두고 있으며, 이 프로젝트에서는 응답 바디를
 * 프로젝트 공통 엔벨로프({@link ApiResponse})와 동일한 형식으로 맞춘다.</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * 인증 실패 시 401 응답을 공통 엔벨로프 형식으로 작성한다.
     *
     * @param request       인증에 실패한 원본 요청
     * @param response      클라이언트에 내려줄 응답
     * @param authException Spring Security가 전달한 인증 예외
     * @throws IOException 응답 바디를 작성하는 중 입출력 오류가 발생한 경우
     */
    @Override
    public void commence(HttpServletRequest request,
                          HttpServletResponse response,
                          AuthenticationException authException) throws IOException {

        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        ApiResponse<Void> body = ApiResponse.error(new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage()));

        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
