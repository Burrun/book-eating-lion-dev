package com.bookeatinglion.order.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoPayApproval;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MockKakaoPayClientTest {

    @Test
    void 승인하면_cid로_시작하는_tid와_승인번호를_생성한다() {
        MockKakaoPayClient client = new MockKakaoPayClient();
        ReflectionTestUtils.setField(client, "cid", "TC0ONETIME");

        KakaoPayApproval approval = client.approve(100L, 10000);

        assertThat(approval.tid()).startsWith("TC0ONETIME-");
        assertThat(approval.approvalNumber()).isNotBlank();
    }

    @Test
    void 취소는_예외없이_끝난다() {
        MockKakaoPayClient client = new MockKakaoPayClient();
        ReflectionTestUtils.setField(client, "cid", "TC0ONETIME");

        assertThatCode(() -> client.cancel("TC0ONETIME-ABC", 10000)).doesNotThrowAnyException();
    }
}
