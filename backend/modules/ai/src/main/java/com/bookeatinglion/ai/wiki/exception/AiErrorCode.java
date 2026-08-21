package com.bookeatinglion.ai.wiki.exception;

import org.springframework.http.HttpStatus;

public enum AiErrorCode {

    /** 일일 쿼터 초과. 서버 장애가 아니라 "지금 붐빔"이므로 5xx 가 아니다. */
    QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    AiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
