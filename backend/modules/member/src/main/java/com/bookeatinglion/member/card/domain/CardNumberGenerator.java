package com.bookeatinglion.member.card.domain;

import java.security.SecureRandom;

/**
 * 레거시(book-eating-lion-bata) CardCommandService 의 마스킹 규칙을 계승하되 BIN 을
 * "5361-"로 바꿨다(이번 요청 스펙). 뒤 4자리만 노출하고 나머지는 마스킹한 채로 저장한다 —
 * 실제 카드망 발급이 아니라 이 서비스가 만들어내는 목(mock) 식별자이기 때문에 가능하다.
 */
public final class CardNumberGenerator {

    private static final String BIN = "5361";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CardNumberGenerator() {}

    public static String generateMasked() {
        int last4 = RANDOM.nextInt(10000);
        return BIN + "-****-****-" + String.format("%04d", last4);
    }
}
