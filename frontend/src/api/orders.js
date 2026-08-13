import { apiClient, unwrap } from "./client.ts";
import { mockCreateOrder } from "../mocks/orders.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// POST /api/orders — 주문 생성. request 형태는 CreateOrderRequest(api/types.ts) 참고.
// 카카오페이 결제 승인(POST /api/payments/kakao/approve), 주문 조회/취소는 이번 스코프 밖.
export async function createOrder(request) {
  if (USE_MOCK) return mockCreateOrder(request);
  return unwrap(apiClient.post("/orders", request));
}
