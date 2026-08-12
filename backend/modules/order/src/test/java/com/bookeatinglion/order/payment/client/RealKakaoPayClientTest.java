package com.bookeatinglion.order.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoApproveResult;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoReadyResult;
import com.bookeatinglion.order.payment.exception.KakaoPayApiException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RealKakaoPayClientTest {

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Mock
    private RestTemplate restTemplate;

    private RealKakaoPayClient client;

    private void setUp() {
        client = new RealKakaoPayClient(restTemplate);
        ReflectionTestUtils.setField(client, "secretKey", "TEST_SECRET_KEY");
        ReflectionTestUtils.setField(client, "cid", "TC0ONETIME");
        ReflectionTestUtils.setField(client, "apiUrl", "https://open-api.kakaopay.com");
        ReflectionTestUtils.setField(client, "frontendCallbackUrl", "http://localhost:5173/payments/kakao/callback");
    }

    @SuppressWarnings("unchecked")
    private void stubPost(String url, Map<String, Object> responseBody) {
        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ready는_SECRET_KEY_헤더와_필수_파라미터를_보낸다() {
        setUp();
        Map<String, Object> response = new HashMap<>();
        response.put("tid", "T123456789");
        response.put("next_redirect_pc_url", "https://mockup-pg-web.kakao.com/pc");
        stubPost("https://open-api.kakaopay.com/online/v1/payment/ready", response);

        KakaoReadyResult result = client.ready(1L, MEMBER_ID, "도서 주문 #1", 10000);

        assertThat(result.tid()).isEqualTo("T123456789");
        assertThat(result.nextRedirectPcUrl()).isEqualTo("https://mockup-pg-web.kakao.com/pc");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate)
                .postForEntity(
                        eq("https://open-api.kakaopay.com/online/v1/payment/ready"), captor.capture(), eq(Map.class));

        HttpEntity<Map<String, Object>> entity = captor.getValue();
        HttpHeaders headers = entity.getHeaders();
        assertThat(headers.getFirst("Authorization")).isEqualTo("SECRET_KEY TEST_SECRET_KEY");

        Map<String, Object> body = entity.getBody();
        assertThat(body.get("cid")).isEqualTo("TC0ONETIME");
        assertThat(body.get("partner_order_id")).isEqualTo("1");
        assertThat(body.get("partner_user_id")).isEqualTo(MEMBER_ID);
        assertThat(body.get("total_amount")).isEqualTo(10000);
        assertThat(body.get("approval_url")).isEqualTo("http://localhost:5173/payments/kakao/callback?orderId=1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void approve는_카드정보가_있으면_approved_id를_승인번호로_쓴다() {
        setUp();
        Map<String, Object> cardInfo = new HashMap<>();
        cardInfo.put("approved_id", "CARD_APPROVAL_123");
        Map<String, Object> response = new HashMap<>();
        response.put("aid", "A987654321");
        response.put("card_info", cardInfo);
        stubPost("https://open-api.kakaopay.com/online/v1/payment/approve", response);

        KakaoApproveResult result = client.approve(1L, MEMBER_ID, "T123456789", "pg-token-value");

        assertThat(result.approvalNumber()).isEqualTo("CARD_APPROVAL_123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void approve는_카드정보가_없으면_aid를_승인번호로_쓴다() {
        setUp();
        Map<String, Object> response = new HashMap<>();
        response.put("aid", "A987654321");
        stubPost("https://open-api.kakaopay.com/online/v1/payment/approve", response);

        KakaoApproveResult result = client.approve(1L, MEMBER_ID, "T123456789", "pg-token-value");

        assertThat(result.approvalNumber()).isEqualTo("A987654321");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancel은_취소_파라미터를_보낸다() {
        setUp();
        stubPost("https://open-api.kakaopay.com/online/v1/payment/cancel", new HashMap<>());

        client.cancel("T123456789", 5000);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate)
                .postForEntity(
                        eq("https://open-api.kakaopay.com/online/v1/payment/cancel"), captor.capture(), eq(Map.class));

        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body.get("tid")).isEqualTo("T123456789");
        assertThat(body.get("cancel_amount")).isEqualTo(5000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 네트워크_오류는_KakaoPayApiException으로_변환된다() {
        setUp();
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("연결 실패"));

        assertThatThrownBy(() -> client.ready(1L, MEMBER_ID, "도서 주문 #1", 10000))
                .isInstanceOf(KakaoPayApiException.class);
    }
}
