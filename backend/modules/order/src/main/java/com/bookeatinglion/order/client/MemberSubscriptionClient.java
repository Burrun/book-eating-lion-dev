package com.bookeatinglion.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 구독권 결제가 확정되면 member-service 에 구독 생성을 요청한다. order-service 의 세 번째
 * outbound 클라이언트다(CatalogClient, CardClient 에 이어 §7.6 예외).
 *
 * <p>🔴 <b>fallback 을 두지 않는다.</b> 다른 둘과 정반대다 — CatalogClient 는 degrade 가격을
 * 주문에서 거부하고, CardClient 는 무응답을 결제 거절로 바꾸면 되지만, 이 호출은 <b>이미 돈이
 * 빠져나간 뒤</b>에 일어난다. 조용히 degrade 하면 "결제는 됐는데 구독이 없다"가 아무 흔적 없이
 * 지나간다. 예외를 그대로 올려서 호출측(OrderService)이 ERROR 로그로 남기게 한다.
 */
@FeignClient(name = "member-subscription", url = "${services.member.url}")
public interface MemberSubscriptionClient {

    /**
     * 구독권을 결제 받아도 되는지 미리 본다. 이미 ACTIVE 면 주문을 거부해서 돈을 받지 않는다 —
     * 받은 뒤에 되돌리려면 환불이 필요한데, 그 경로가 없다.
     *
     * <p>ai-service 의 같은 이름 클라이언트와 같은 계약을 쓴다(member-v1.yaml).
     */
    @GetMapping("/internal/members/{memberId}/subscription-status")
    SubscriptionStatus getSubscriptionStatus(@PathVariable("memberId") String memberId);

    /** 멤버 계약: 멱등하다. 이미 ACTIVE 면 그 구독을 그대로 돌려준다(InternalSubscriptionController 참고). */
    @PostMapping("/internal/members/{memberId}/subscription")
    SubscriptionView activate(@PathVariable("memberId") String memberId, @RequestBody ActivateRequest request);

    /** planType 은 member 의 PlanType enum 이름(MONTHLY / YEARLY)이다. */
    record ActivateRequest(String planType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SubscriptionStatus(String memberId, boolean subscribed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SubscriptionView(Long id, String status, String planType) {}
}
