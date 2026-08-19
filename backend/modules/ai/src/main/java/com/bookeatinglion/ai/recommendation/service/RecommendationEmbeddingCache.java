package com.bookeatinglion.ai.recommendation.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendationEmbeddingCache {

    private static final String PREFIX = "ai:recommendation:embedding:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public float[] getOrCreate(long bookId, String sourceText, Supplier<float[]> generator) {
        String key = PREFIX + bookId + ":" + digest(sourceText);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return decode(cached);
            }
        } catch (RuntimeException ignored) {
            // Redis 장애는 임베딩 재생성으로 degrade 한다.
        }

        float[] generated = generator.get();
        try {
            redis.opsForValue().set(key, encode(generated), TTL);
        } catch (RuntimeException ignored) {
            // 캐시 저장 실패가 추천 자체를 실패시키면 안 된다.
        }
        return generated;
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("추천 임베딩 캐시 키 생성 실패", e);
        }
    }

    private static String encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static float[] decode(String encoded) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
        float[] vector = new float[buffer.remaining() / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
