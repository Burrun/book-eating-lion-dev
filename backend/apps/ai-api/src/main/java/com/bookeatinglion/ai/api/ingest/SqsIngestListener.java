package com.bookeatinglion.ai.api.ingest;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.api.sqs.SqsWorker;
import com.bookeatinglion.ai.ingest.BookIngestService;
import com.bookeatinglion.ai.ingest.BookIngestService.IngestCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * 책 등록 이벤트를 받아 인제스트한다. catalog-service 가 EPUB 등록 시 발행한다.
 *
 * <p>🔴 <b>가시성 시간 주의.</b> 청크 하나당 임베딩 1회이고 장편은 200~500청크다. 처리 시간이
 * 큐의 Visibility Timeout 을 넘으면 <b>처리 중인 책이 다시 배달되어 중복 임베딩(=중복 과금)</b>
 * 이 난다. 큐를 300초로 잡았다면 그 안에 끝나는지 로그의 소요시간으로 확인해야 한다.
 *
 * <p>한 번에 1건만 받는다. 임베딩이 외부 API 라 여러 권을 동시에 돌리면 Bedrock 스로틀링에
 * 걸리고, 그때 재시도가 겹치면 비용만 늘어난다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.sqs.enabled", havingValue = "true")
public class SqsIngestListener implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SqsIngestListener.class);

    private final SqsWorker worker;

    public SqsIngestListener(SqsClient sqs, BookIngestService ingestService, ObjectMapper mapper, AiProperties props) {
        AiProperties.Sqs config = props.sqs();
        this.worker = new SqsWorker(
                sqs,
                "ingest",
                config.queueUrl(),
                config.waitTimeSeconds(),
                config.maxMessages(),
                message -> handle(message, ingestService, mapper));
    }

    @Override
    public void run(ApplicationArguments args) {
        worker.start();
    }

    /**
     * 예외를 삼키지 않는다 — {@link SqsWorker} 가 "정상 반환 = 삭제" 로 판단한다.
     *
     * <p>다만 {@code epubS3Key} 가 없는 메시지는 재시도해도 영원히 성공하지 않으므로 정상
     * 반환해 삭제시킨다. 이건 실패가 아니라 "인제스트 대상이 아님"이다.
     */
    private static void handle(Message message, BookIngestService ingestService, ObjectMapper mapper) {
        BookIngestMessage payload;
        try {
            payload = mapper.readValue(message.body(), BookIngestMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("인제스트 메시지 파싱 실패: " + message.body(), e);
        }

        if (payload.epubS3Key() == null || payload.epubS3Key().isBlank()) {
            log.warn("epubS3Key 가 없다 — 인제스트 대상이 아니다. 메시지를 지운다. bookId={}", payload.bookId());
            return;
        }

        ingestService.ingest(
                new IngestCommand(payload.bookId(), payload.title(), payload.category(), payload.epubS3Key()));
    }

    @PreDestroy
    void stop() {
        worker.close();
    }

    /** 발행자는 catalog-service 다. 계약은 docs/ai-integration.md 에 있다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookIngestMessage(long bookId, String title, String category, String epubS3Key) {}
}
