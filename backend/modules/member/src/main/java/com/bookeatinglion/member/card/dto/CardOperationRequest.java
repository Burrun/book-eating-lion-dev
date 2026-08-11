package com.bookeatinglion.member.card.dto;

import jakarta.validation.constraints.Min;

/** order-service 의 CardClient.CardOperationRequest 와 1:1 (member-v1.yaml /internal/cards 계약). */
public record CardOperationRequest(@Min(1) long amount) {}
