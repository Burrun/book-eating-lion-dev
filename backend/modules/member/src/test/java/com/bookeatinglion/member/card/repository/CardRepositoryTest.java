package com.bookeatinglion.member.card.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.card.domain.Card;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class CardRepositoryTest {

    @Autowired
    private CardRepository cardRepository;

    @Test
    void 회원ID로_카드_목록을_조회한다() {
        cardRepository.save(new Card(1L, "token-1", "5361-****-****-1111", 1_000_000L));
        cardRepository.save(new Card(1L, "token-2", "5361-****-****-2222", 500_000L));
        cardRepository.save(new Card(2L, "token-3", "5361-****-****-3333", 1_000_000L));

        List<Card> cards = cardRepository.findByMemberId(1L);

        assertThat(cards).hasSize(2);
    }
}
