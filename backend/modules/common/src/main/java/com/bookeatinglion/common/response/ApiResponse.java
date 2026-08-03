package com.bookeatinglion.common.response;

import lombok.Getter;

/**
 * 프로젝트 전역에서 사용하는 공통 API 응답 엔벨로프(Envelope) 클래스.
 *
 * <p>모든 컨트롤러는 실제 응답 바디를 이 클래스로 감싸서 반환해야 하며,
 * {@code /docs/API 명세서 초안.md}에 정의된 아래 포맷을 따른다.</p>
 *
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { ... },
 *   "error": null
 * }
 * }</pre>
 *
 * @param <T> 성공 시 {@code data} 필드에 담길 응답 바디 타입
 */
@Getter
public class ApiResponse<T> {

    /** 요청 처리 성공 여부. */
    private final boolean success;

    /** 성공 시 실제 응답 데이터. 실패 시에는 항상 {@code null}이다. */
    private final T data;

    /** 실패 시 에러 상세 정보. 성공 시에는 항상 {@code null}이다. */
    private final ErrorResponse error;

    private ApiResponse(boolean success, T data, ErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    /**
     * 성공 응답을 생성한다.
     *
     * @param data 응답으로 내려줄 데이터. 별도 데이터가 없다면 {@code null}을 전달할 수 있다.
     * @param <T>  데이터 타입
     * @return {@code success=true}, {@code error=null}인 {@link ApiResponse}
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 실패 응답을 생성한다.
     *
     * @param error 에러 코드와 메시지를 담은 {@link ErrorResponse}
     * @param <T>   데이터 타입(실패 응답에서는 항상 {@code null}로 채워진다)
     * @return {@code success=false}, {@code data=null}인 {@link ApiResponse}
     */
    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
