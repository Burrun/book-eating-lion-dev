package com.bookeatinglion.ai.wiki.exception;

/**
 * 일일 쿼터 초과. {@code retryAfterSeconds} 는 응답의 {@code Retry-After} 헤더가 된다 —
 * 헤더가 없으면 클라이언트가 즉시 재시도해서 초과 상태가 계속 이어진다.
 */
public class QuotaExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public QuotaExceededException(long retryAfterSeconds) {
        super("일일 질의 한도를 초과했습니다.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
