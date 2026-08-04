package com.bookeatinglion.isbn.service;

import com.bookeatinglion.isbn.client.AladinBookApiClient;
import com.bookeatinglion.isbn.client.KakaoBookApiClient;
import com.bookeatinglion.isbn.dto.IsbnLookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IsbnLookupServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private KakaoBookApiClient kakaoBookApiClient;

    @Mock
    private AladinBookApiClient aladinBookApiClient;

    private IsbnLookupService isbnLookupService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        isbnLookupService = new IsbnLookupService(redisTemplate, kakaoBookApiClient, aladinBookApiClient);
    }

    @Test
    void 캐시에_있으면_외부_API를_호출하지_않고_캐시값을_반환한다() {
        IsbnLookupResponse cached = new IsbnLookupResponse("9791100000001", "캐시된책", "저자", "출판사", "cover.jpg", "설명");
        when(valueOperations.get("isbn:lookup:9791100000001")).thenReturn(cached);

        IsbnLookupResponse result = isbnLookupService.lookup("9791100000001");

        assertThat(result).isEqualTo(cached);
        verify(kakaoBookApiClient, never()).lookup(anyString());
        verify(aladinBookApiClient, never()).lookup(anyString());
    }

    @Test
    void 캐시가_없으면_카카오_API를_조회하고_24시간_TTL로_캐싱한다() {
        when(valueOperations.get(anyString())).thenReturn(null);
        IsbnLookupResponse kakaoResponse = new IsbnLookupResponse("9791100000001", "카카오책", "저자", "출판사", "cover.jpg", "설명");
        when(kakaoBookApiClient.lookup("9791100000001")).thenReturn(Optional.of(kakaoResponse));

        IsbnLookupResponse result = isbnLookupService.lookup("9791100000001");

        assertThat(result).isEqualTo(kakaoResponse);
        verify(valueOperations).set(eq("isbn:lookup:9791100000001"), eq(kakaoResponse), eq(Duration.ofHours(24)));
        verify(aladinBookApiClient, never()).lookup(anyString());
    }

    @Test
    void 카카오가_실패하면_알라딘을_시도한다() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(kakaoBookApiClient.lookup("9791100000001")).thenReturn(Optional.empty());
        IsbnLookupResponse aladinResponse = new IsbnLookupResponse("9791100000001", "알라딘책", "저자", "출판사", "cover.jpg", "설명");
        when(aladinBookApiClient.lookup("9791100000001")).thenReturn(Optional.of(aladinResponse));

        IsbnLookupResponse result = isbnLookupService.lookup("9791100000001");

        assertThat(result).isEqualTo(aladinResponse);
        verify(valueOperations).set(eq("isbn:lookup:9791100000001"), eq(aladinResponse), any(Duration.class));
    }

    @Test
    void 모든_외부_API가_실패하면_더미_응답을_반환하고_캐싱하지_않는다() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(kakaoBookApiClient.lookup(anyString())).thenReturn(Optional.empty());
        when(aladinBookApiClient.lookup(anyString())).thenReturn(Optional.empty());

        IsbnLookupResponse result = isbnLookupService.lookup("9791100099999");

        assertThat(result.isbn()).isEqualTo("9791100099999");
        assertThat(result.title()).contains("9791100099999");
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }
}
