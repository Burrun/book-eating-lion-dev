package com.bookeatinglion.member.card.service;

import com.bookeatinglion.member.card.domain.Card;
import com.bookeatinglion.member.card.domain.CardNumberGenerator;
import com.bookeatinglion.member.card.domain.CardStatus;
import com.bookeatinglion.member.card.dto.CardOperationResult;
import com.bookeatinglion.member.card.dto.CardResponse;
import com.bookeatinglion.member.card.dto.IssueCardRequest;
import com.bookeatinglion.member.card.exception.CardNotFoundException;
import com.bookeatinglion.member.card.exception.UnauthorizedCardAccessException;
import com.bookeatinglion.member.card.repository.CardRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardService {

    private static final long DEFAULT_MONTHLY_LIMIT = 1_000_000L;

    private final CardRepository cardRepository;

    @Transactional
    public CardResponse issueCard(String memberSub, IssueCardRequest request) {
        long monthlyLimit = request.monthlyLimit() != null ? request.monthlyLimit() : DEFAULT_MONTHLY_LIMIT;

        Card card = new Card(
                memberSub, UUID.randomUUID().toString(), CardNumberGenerator.generateMasked(), monthlyLimit);
        return CardResponse.from(cardRepository.save(card));
    }

    public List<CardResponse> getMyCards(String memberSub) {
        return cardRepository.findByMemberSub(memberSub).stream()
                .map(CardResponse::from)
                .toList();
    }

    @Transactional
    public CardResponse changeStatus(String memberSub, Long cardId, CardStatus newStatus) {
        Card card = getOwnedCard(memberSub, cardId);
        card.changeStatus(newStatus);
        return CardResponse.from(card);
    }

    /** /internal/cards/{cardId}/deduct. 승인 실패는 예외가 아니라 approved=false 인 정상 응답이다. */
    @Transactional
    public CardOperationResult deduct(Long cardId, long amount) {
        Card card = cardRepository.findById(cardId).orElse(null);
        if (card == null) {
            return CardOperationResult.declined("카드를 찾을 수 없습니다: " + cardId);
        }
        if (!card.deduct(amount)) {
            return CardOperationResult.declined("한도가 부족하거나 사용할 수 없는 카드입니다: " + cardId);
        }
        return CardOperationResult.approve();
    }

    /** /internal/cards/{cardId}/restore. */
    @Transactional
    public CardOperationResult restore(Long cardId, long amount) {
        Card card = cardRepository.findById(cardId).orElse(null);
        if (card == null) {
            return CardOperationResult.declined("카드를 찾을 수 없습니다: " + cardId);
        }
        card.restore(amount);
        return CardOperationResult.approve();
    }

    private Card getOwnedCard(String memberSub, Long cardId) {
        Card card = cardRepository.findById(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
        if (!card.isOwnedBy(memberSub)) {
            throw new UnauthorizedCardAccessException(cardId);
        }
        return card;
    }
}
