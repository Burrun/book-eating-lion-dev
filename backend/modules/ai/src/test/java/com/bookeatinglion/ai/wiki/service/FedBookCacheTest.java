package com.bookeatinglion.ai.wiki.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.ai.wiki.repository.FedBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class FedBookCacheTest {

    private static final String MEMBER_ID = "test-cognito-sub-1";
    private static final String KEY = "ai:fed:" + MEMBER_ID;

    private StringRedisTemplate redis;
    private SetOperations<String, String> setOperations;
    private FedBookCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        FedBookRepository fedBookRepository = mock(FedBookRepository.class);
        when(redis.opsForSet()).thenReturn(setOperations);

        cache = new FedBookCache(redis, fedBookRepository);
    }

    @Test
    void SADD_실패하면_해당_회원_캐시를_통째로_지운다() {
        when(setOperations.add(anyString(), anyString())).thenThrow(new QueryTimeoutException("timeout"));

        cache.add(MEMBER_ID, 101L);

        verify(redis).delete(KEY);
    }

    @Test
    void 캐시_무효화마저_실패해도_예외를_올리지_않는다() {
        when(setOperations.add(anyString(), anyString())).thenThrow(new QueryTimeoutException("timeout"));
        when(redis.delete(anyString())).thenThrow(new QueryTimeoutException("timeout"));

        cache.add(MEMBER_ID, 101L);
    }

    @Test
    void SADD_성공하면_캐시를_지우지_않는다() {
        cache.add(MEMBER_ID, 101L);

        verify(redis, never()).delete(anyString());
    }
}
