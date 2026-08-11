package com.bookeatinglion.order.payment.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실제 PG 호출 없이 tid/승인번호를 생성한다. cid 는 카카오페이 테스트 가맹점 코드
 * (TC0ONETIME)를 기본값으로 두어, 나중에 실키를 발급받으면 설정값만 바꿔 실 연동으로
 * 전환할 수 있게 자리를 맞춰뒀다.
 */
@Slf4j
@Component
public class MockKakaoPayClient implements KakaoPayClient {

    @Value("${kakaopay.cid:TC0ONETIME}")
    private String cid;

    @Override
    public KakaoPayApproval approve(Long orderId, int amount) {
        String tid = cid + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String approvalNumber = "A" + System.currentTimeMillis();
        log.info("[MockKakaoPay] 승인 orderId={}, amount={}, tid={}", orderId, amount, tid);
        return new KakaoPayApproval(tid, approvalNumber);
    }

    @Override
    public void cancel(String tid, int amount) {
        log.info("[MockKakaoPay] 취소 tid={}, amount={}", tid, amount);
    }
}
