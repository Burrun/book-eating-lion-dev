package com.bookeatinglion.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * member-service 의 가상카드 한도를 동기 조작한다(§7.6 두 번째 예외). member-v1.yaml 의
 * /internal/cards/** 계약을 그대로 쓴다 — member-service 쪽 Java 구현은 아직 없고 로컬에서는
 * Prism mock 이 응답한다(계약이 곧 mock 이라는 이 저장소의 원칙, contracts/README.md).
 *
 * deduct/restore 모두 실패를 예외가 아니라 approved=false 로 표현한다 — "한도 초과"는
 * 정상적인 업무 결과이지 시스템 오류가 아니기 때문이다. fallback 은 member-service 가
 * 무응답일 때도 같은 모양(approved=false)으로 degrade 시켜 호출부(PaymentService)가
 * 두 경우(거절 vs 무응답)를 똑같이 "승인 실패"로 다루게 한다.
 */
@FeignClient(name = "member-card", url = "${services.member.url}", fallback = CardClientFallback.class)
public interface CardClient {

    @PostMapping("/internal/cards/{cardId}/deduct")
    CardOperationResult deduct(@PathVariable("cardId") Long cardId, @RequestBody CardOperationRequest request);

    @PostMapping("/internal/cards/{cardId}/restore")
    CardOperationResult restore(@PathVariable("cardId") Long cardId, @RequestBody CardOperationRequest request);

    record CardOperationRequest(int amount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CardOperationResult(boolean approved, String message) {}
}
