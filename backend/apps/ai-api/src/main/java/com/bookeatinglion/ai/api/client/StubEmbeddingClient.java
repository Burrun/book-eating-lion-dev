package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.client.EmbeddingClient;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Bedrock 연동 전 자리를 채우는 스텁. 차원(1024)과 반환 형식(pgvector 리터럴)은
 * 실제 구현과 동일해서, 나중에 교체해도 스키마와 인덱스는 그대로 쓸 수 있다.
 *
 * 실제 Bedrock 호출은 Phase 1 의 ai 팀 산출물이다. 이 클래스를 지우고
 * 같은 인터페이스의 빈을 등록하면 나머지 코드는 손대지 않아도 된다.
 */
@Component
@ConditionalOnMissingBean(name = "bedrockEmbeddingClient")
public class StubEmbeddingClient implements EmbeddingClient {

    private final Random random = new Random(42);

    @Override
    public String embed(String text) {
        return IntStream.range(0, DIMENSION)
                .mapToObj(i -> String.valueOf(random.nextFloat()))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
