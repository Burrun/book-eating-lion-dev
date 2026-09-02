package com.bookeatinglion.member.card.controller;

import com.bookeatinglion.member.card.dto.CardOperationRequest;
import com.bookeatinglion.member.card.dto.CardOperationResult;
import com.bookeatinglion.member.card.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /internal/** 은 서비스 간 전용이다(order-service 의 CardClient). 외부에는 노출하지 않는다
 * — Ingress 는 /api/** 만 라우팅하고 NetworkPolicy 가 클러스터 밖 트래픽을 막는다.
 */
@RestController
@RequestMapping("/internal/cards")
@RequiredArgsConstructor
public class InternalCardController {

    private final CardService cardService;

    @PostMapping("/{cardId}/deduct")
    public CardOperationResult deduct(@PathVariable Long cardId, @Valid @RequestBody CardOperationRequest request) {
        return cardService.deduct(cardId, request.amount());
    }

    @PostMapping("/{cardId}/restore")
    public CardOperationResult restore(@PathVariable Long cardId, @Valid @RequestBody CardOperationRequest request) {
        return cardService.restore(cardId, request.amount());
    }
}
