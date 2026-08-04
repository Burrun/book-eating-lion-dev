package com.bookeatinglion.isbn.service;

import com.bookeatinglion.isbn.client.AladinBookApiClient;
import com.bookeatinglion.isbn.client.KakaoBookApiClient;
import com.bookeatinglion.isbn.dto.IsbnLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IsbnLookupService {

    private static final String CACHE_KEY_PREFIX = "isbn:lookup:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final KakaoBookApiClient kakaoBookApiClient;
    private final AladinBookApiClient aladinBookApiClient;

    public IsbnLookupResponse lookup(String isbn) {
        String cacheKey = CACHE_KEY_PREFIX + isbn;
        IsbnLookupResponse cached = (IsbnLookupResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        IsbnLookupResponse response = kakaoBookApiClient.lookup(isbn)
                .or(() -> aladinBookApiClient.lookup(isbn))
                .orElse(null);

        if (response != null) {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
            return response;
        }

        return fallback(isbn);
    }

    private IsbnLookupResponse fallback(String isbn) {
        return new IsbnLookupResponse(isbn, "등록되지 않은 도서 (ISBN: " + isbn + ")", null, null, null, null);
    }
}
