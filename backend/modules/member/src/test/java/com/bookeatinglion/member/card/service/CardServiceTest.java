package com.bookeatinglion.member.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookeatinglion.member.card.domain.Card;
import com.bookeatinglion.member.card.domain.CardStatus;
import com.bookeatinglion.member.card.dto.CardOperationResult;
import com.bookeatinglion.member.card.dto.CardResponse;
import com.bookeatinglion.member.card.dto.IssueCardRequest;
import com.bookeatinglion.member.card.exception.CardNotFoundException;
import com.bookeatinglion.member.card.exception.InvalidCardStatusChangeException;
import com.bookeatinglion.member.card.exception.UnauthorizedCardAccessException;
import com.bookeatinglion.member.card.repository.CardRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @InjectMocks
    private CardService cardService;

    private Card card(Long id, Long memberId, long monthlyLimit, long currentUsage) {
        Card card = new Card(memberId, "token-" + id, "5361-****-****-0001", monthlyLimit);
        ReflectionTestUtils.setField(card, "id", id);
        ReflectionTestUtils.setField(card, "currentUsage", currentUsage);
        return card;
    }

    @Test
    void 카드를_발급하면_기본한도가_적용된다() {
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardResponse response = cardService.issueCard(1L, new IssueCardRequest(null));

        assertThat(response.monthlyLimit()).isEqualTo(1_000_000L);
        assertThat(response.cardStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(response.maskedCardNumber()).startsWith("5361-");
    }

    @Test
    void 월한도를_지정하면_그대로_반영된다() {
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardResponse response = cardService.issueCard(1L, new IssueCardRequest(300_000L));

        assertThat(response.monthlyLimit()).isEqualTo(300_000L);
    }

    @Test
    void 보유_카드_목록을_조회한다() {
        when(cardRepository.findByMemberId(1L)).thenReturn(List.of(card(1L, 1L, 1_000_000L, 0L)));

        List<CardResponse> cards = cardService.getMyCards(1L);

        assertThat(cards).hasSize(1);
    }

    @Test
    void 본인_카드_상태를_변경한다() {
        Card card = card(1L, 1L, 1_000_000L, 0L);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        CardResponse response = cardService.changeStatus(1L, 1L, CardStatus.SUSPENDED);

        assertThat(response.cardStatus()).isEqualTo(CardStatus.SUSPENDED);
    }

    @Test
    void 타인의_카드_상태변경은_예외를_던진다() {
        Card card = card(1L, 2L, 1_000_000L, 0L);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.changeStatus(1L, 1L, CardStatus.SUSPENDED))
                .isInstanceOf(UnauthorizedCardAccessException.class);
    }

    @Test
    void 존재하지_않는_카드_상태변경은_예외를_던진다() {
        when(cardRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.changeStatus(1L, 999L, CardStatus.SUSPENDED))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void CLOSED_카드는_상태를_변경할_수_없다() {
        Card card = card(1L, 1L, 1_000_000L, 0L);
        card.changeStatus(CardStatus.CLOSED);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.changeStatus(1L, 1L, CardStatus.ACTIVE))
                .isInstanceOf(InvalidCardStatusChangeException.class);
    }

    @Test
    void 한도내_차감은_승인된다() {
        Card card = card(1L, 1L, 10_000L, 0L);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        CardOperationResult result = cardService.deduct(1L, 5_000L);

        assertThat(result.approved()).isTrue();
        assertThat(card.getCurrentUsage()).isEqualTo(5_000L);
    }

    @Test
    void 한도초과_차감은_거절된다() {
        Card card = card(1L, 1L, 10_000L, 8_000L);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        CardOperationResult result = cardService.deduct(1L, 5_000L);

        assertThat(result.approved()).isFalse();
        assertThat(card.getCurrentUsage()).isEqualTo(8_000L);
    }

    @Test
    void 존재하지_않는_카드_차감은_거절된다() {
        when(cardRepository.findById(999L)).thenReturn(Optional.empty());

        CardOperationResult result = cardService.deduct(999L, 1_000L);

        assertThat(result.approved()).isFalse();
    }

    @Test
    void 정지된_카드는_차감이_거절된다() {
        Card card = card(1L, 1L, 10_000L, 0L);
        card.changeStatus(CardStatus.SUSPENDED);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        CardOperationResult result = cardService.deduct(1L, 1_000L);

        assertThat(result.approved()).isFalse();
    }

    @Test
    void 한도를_복구한다() {
        Card card = card(1L, 1L, 10_000L, 5_000L);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        CardOperationResult result = cardService.restore(1L, 3_000L);

        assertThat(result.approved()).isTrue();
        assertThat(card.getCurrentUsage()).isEqualTo(2_000L);
    }
}
