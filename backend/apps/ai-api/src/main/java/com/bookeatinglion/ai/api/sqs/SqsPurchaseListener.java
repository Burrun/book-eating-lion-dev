package com.bookeatinglion.ai.api.sqs;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.wiki.event.BookPurchaseEvent;
import com.bookeatinglion.ai.wiki.service.BookPurchaseHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * 구매 이벤트 수신. order-service 가 결제 완료 시 발행한다.
 *
 * <p>이 이벤트가 곧 <b>검색 권한</b>이다 — 구매한 책만 RAG 질의 대상이 된다. 그래서 유실되면
 * 사용자가 산 책을 읽지 못하고, 그 사실이 에러로 드러나지 않는다(그냥 "근거를 못 찾았습니다"가
 * 나온다). 유실 불가라서 Redis 가 아니라 SQS 다.
 *
 * <p>인제스트와 달리 한 번에 여러 건을 받는다. 처리가 DB 한 줄 + Redis 한 줄이라 외부 API
 * 스로틀링 걱정이 없다 — 임베딩을 타는 인제스트와 같은 값을 쓸 이유가 없다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.sqs.purchase.enabled", havingValue = "true")
public class SqsPurchaseListener implements ApplicationRunner {

    /** 처리가 가벼워 배치로 받는다. SQS 한 번 호출의 상한이 10이다. */
    private static final int MAX_MESSAGES = 10;

    private final SqsWorker worker;

    public SqsPurchaseListener(SqsClient sqs, BookPurchaseHandler handler, ObjectMapper mapper, AiProperties props) {
        AiProperties.Sqs config = props.sqs();
        this.worker = new SqsWorker(
                sqs,
                "purchase",
                config.purchase().queueUrl(),
                config.waitTimeSeconds(),
                MAX_MESSAGES,
                message -> handle(message, handler, mapper));
    }

    @Override
    public void run(ApplicationArguments args) {
        worker.start();
    }

    /**
     * 예외를 삼키지 않는다. {@link SqsWorker} 가 "정상 반환 = 삭제" 로 판단하므로, 여기서
     * 잡아버리면 처리하지 못한 메시지를 지운 것이 된다.
     */
    private static void handle(Message message, BookPurchaseHandler handler, ObjectMapper mapper) {
        try {
            handler.handle(mapper.readValue(message.body(), BookPurchaseEvent.class));
        } catch (Exception e) {
            throw new IllegalStateException("구매 이벤트 처리 실패: " + message.body(), e);
        }
    }

    @PreDestroy
    void stop() {
        worker.close();
    }
}
