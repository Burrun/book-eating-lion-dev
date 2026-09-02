package com.bookeatinglion.member.card.dto;

import com.bookeatinglion.member.card.domain.Card;
import com.bookeatinglion.member.card.domain.CardStatus;
import java.time.LocalDate;

public record CardResponse(
        Long cardId,
        String maskedCardNumber,
        CardStatus cardStatus,
        long monthlyLimit,
        long currentUsage,
        LocalDate issuedDate,
        LocalDate expiryDate) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getMaskedCardNumber(),
                card.getCardStatus(),
                card.getMonthlyLimit(),
                card.getCurrentUsage(),
                card.getIssuedDate(),
                card.getExpiryDate());
    }
}
