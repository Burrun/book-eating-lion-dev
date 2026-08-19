package com.bookeatinglion.order.api.event;

import com.bookeatinglion.order.event.BookPurchasePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * 구매 확정 이벤트를 book-purchase-queue 로 보낸다. ai-service 의 SqsPurchaseListener 가
 * 이 큐를 읽어 구매한 책을 검색 권한(RAG 대상)으로 적재한다 — 유실되면 사용자가 산 책을
 * 못 찾는데 그 사실이 에러로 드러나지 않는다("근거를 못 찾았습니다"만 나온다).
 *
 * 예외를 여기서 삼키는 이유: 호출부(OrderService)가 afterCommit 훅에서 이 메서드를 부른다
 * — 그 시점엔 주문 트랜잭션이 이미 커밋되어 있어 예외를 던져도 되돌릴 게 없다. memberId/
 * bookId 를 남긴 로그가 Spring 의 일반 "afterCommit threw exception" 로그보다 추적에 낫다.
 */
@Slf4j
@Component
public class SqsBookPurchasePublisher implements BookPurchasePublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.purchase.queue-url}")
    private String queueUrl;

    public SqsBookPurchasePublisher(SqsClient sqsClient, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String memberId, Long bookId) {
        try {
            String body = objectMapper.writeValueAsString(new BookPurchaseMessage(memberId, bookId));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
            log.info("구매 확정 이벤트 발행: memberId={}, bookId={}", memberId, bookId);
        } catch (Exception e) {
            log.error("구매 확정 이벤트 발행 실패: memberId={}, bookId={}", memberId, bookId, e);
        }
    }

    /** ai-service 의 BookPurchaseEvent(memberId, bookId) 와 같은 JSON 스키마다. */
    private record BookPurchaseMessage(String memberId, Long bookId) {}
}
