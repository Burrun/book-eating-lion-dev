package com.bookeatinglion.catalog.api.ingest;

import com.bookeatinglion.book.port.BookIngestPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * 신간 등록 이벤트를 book-ingest-queue 로 보낸다. ai-service 의 SqsIngestListener 가 이 큐를
 * 읽어 EPUB을 다운로드→파싱→임베딩→벡터 적재한다.
 *
 * 예외를 여기서 삼키는 이유: 호출부(AdminBookService)가 afterCommit 훅에서 이 메서드를 부른다
 * — 그 시점엔 도서 등록 트랜잭션이 이미 커밋되어 있어 예외를 던져도 되돌릴 게 없다. bookId를
 * 남긴 로그가 Spring 의 일반 "afterCommit threw exception" 로그보다 추적에 낫다
 * (order-api의 SqsBookPurchasePublisher와 같은 이유).
 */
@Slf4j
@Component
public class SqsBookIngestPublisher implements BookIngestPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.ingest.queue-url}")
    private String queueUrl;

    public SqsBookIngestPublisher(SqsClient sqsClient, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean publish(Long bookId, String title, String category, String epubS3Key) {
        try {
            String body = objectMapper.writeValueAsString(new BookIngestMessage(bookId, title, category, epubS3Key));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
            log.info("신간 등록 이벤트 발행: bookId={}, title={}", bookId, title);
            return true;
        } catch (Exception e) {
            log.error("신간 등록 이벤트 발행 실패: bookId={}, title={}", bookId, title, e);
            return false;
        }
    }

    /** ai-service 의 SqsIngestListener.BookIngestMessage 와 같은 JSON 스키마다. */
    private record BookIngestMessage(long bookId, String title, String category, String epubS3Key) {}
}
