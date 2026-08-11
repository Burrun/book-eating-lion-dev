package com.bookeatinglion.member.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardNumberGeneratorTest {

    @Test
    void BIN_5361과_마스킹_형식을_따른다() {
        String masked = CardNumberGenerator.generateMasked();

        assertThat(masked).matches("5361-\\*{4}-\\*{4}-\\d{4}");
    }
}
