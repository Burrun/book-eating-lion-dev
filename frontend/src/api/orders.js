import { apiClient, unwrap } from "./client.ts";
import { mockApproveKakaoPayment, mockCreateOrder } from "../mocks/orders.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// POST /api/orders — 주문 생성. request 형태는 CreateOrderRequest(api/types.ts) 참고.
// VIRTUAL_CARD는 이 호출 안에서 결제까지 끝나 orderStatus=PAID로 응답한다. KAKAO_PAY는
// 결제 준비(Ready)만 끝나 orderStatus=PENDING_PAYMENT + nextRedirectUrl로 응답하며,
// 최종 승인은 approveKakaoPayment()가 담당한다. 주문 조회/취소는 이번 스코프 밖.
export async function createOrder(request) {
  if (USE_MOCK) return mockCreateOrder(request);
  return unwrap(apiClient.post("/orders", request));
}

// POST /api/payments/kakao/approve — 카카오페이 결제 승인(2단계 중 2단계).
// 카카오 결제 페이지에서 승인 후 리다이렉트로 돌아온 pg_token과, ready 시 발급된 orderId를 전달한다.
export async function approveKakaoPayment(orderId, pgToken) {
  if (USE_MOCK) return mockApproveKakaoPayment(orderId);
  return unwrap(apiClient.post("/payments/kakao/approve", { orderId, pgToken }));
}
