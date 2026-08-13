package com.bookeatinglion.order.payment.client;

import com.bookeatinglion.order.payment.exception.KakaoPayApiException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 레거시(book-eating-lion-bata) KakaoPayService 를 이식했다 — 파라미터 구성과 헤더는 그대로,
 * ready() 만 우리 시스템 사정에 맞게 approval_url 을 프론트 콜백 페이지 하나로 통일했다
 * (레거시는 프론트가 별도 페이지를 갖고 있었지만, 우리는 쿼리 파라미터 status 로 구분한다).
 */
@Slf4j
@Component
public class RealKakaoPayClient implements KakaoPayClient {

    @Value("${kakaopay.secret-key}")
    private String secretKey;

    @Value("${kakaopay.cid}")
    private String cid;

    @Value("${kakaopay.api-url}")
    private String apiUrl;

    @Value("${kakaopay.frontend-callback-url}")
    private String frontendCallbackUrl;

    private final RestTemplate restTemplate;

    public RealKakaoPayClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public KakaoReadyResult ready(Long orderId, String memberId, String itemName, int amount) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("cid", cid);
        parameters.put("partner_order_id", String.valueOf(orderId));
        parameters.put("partner_user_id", memberId);
        parameters.put("item_name", itemName);
        parameters.put("quantity", 1);
        parameters.put("total_amount", amount);
        parameters.put("tax_free_amount", 0);
        // orderId 를 쿼리로 심어둔다 — 카카오가 리다이렉트 시 pg_token 만 붙여 보내므로,
        // approve 호출에 필요한 orderId 를 프론트가 URL 만으로 알 수 있게 하기 위해서다
        // (세션/로컬스토리지에 따로 기억시키지 않아도 새로고침·다른 탭에서 안전하다).
        parameters.put("approval_url", frontendCallbackUrl + "?orderId=" + orderId);
        parameters.put("cancel_url", frontendCallbackUrl + "?orderId=" + orderId + "&status=cancel");
        parameters.put("fail_url", frontendCallbackUrl + "?orderId=" + orderId + "&status=fail");

        Map<String, Object> body = post("/online/v1/payment/ready", parameters);
        String tid = (String) body.get("tid");
        String redirectUrl = (String) body.get("next_redirect_pc_url");
        log.info("[KakaoPay] ready 완료 orderId={}, tid={}", orderId, tid);
        return new KakaoReadyResult(tid, redirectUrl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public KakaoApproveResult approve(Long orderId, String memberId, String tid, String pgToken) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("cid", cid);
        parameters.put("tid", tid);
        parameters.put("partner_order_id", String.valueOf(orderId));
        parameters.put("partner_user_id", memberId);
        parameters.put("pg_token", pgToken);

        Map<String, Object> body = post("/online/v1/payment/approve", parameters);

        String approvalNumber = (String) body.get("aid");
        if (body.get("card_info") instanceof Map<?, ?> cardInfo && cardInfo.get("approved_id") != null) {
            approvalNumber = (String) cardInfo.get("approved_id");
        }
        log.info("[KakaoPay] approve 완료 orderId={}, approvalNumber={}", orderId, approvalNumber);
        return new KakaoApproveResult(approvalNumber);
    }

    @Override
    public void cancel(String tid, int amount) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("cid", cid);
        parameters.put("tid", tid);
        parameters.put("cancel_amount", amount);
        parameters.put("cancel_tax_free_amount", 0);

        post("/online/v1/payment/cancel", parameters);
        log.info("[KakaoPay] cancel 완료 tid={}, amount={}", tid, amount);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> parameters) {
        String url = apiUrl + path;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(parameters, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new KakaoPayApiException("카카오페이 응답이 비어있습니다: " + path);
            }
            return body;
        } catch (RestClientException e) {
            log.error("[KakaoPay] 호출 실패 url={}, message={}", url, e.getMessage(), e);
            throw new KakaoPayApiException("카카오페이 API 호출에 실패했습니다(" + path + "): " + e.getMessage());
        }
    }
}
