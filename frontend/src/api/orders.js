import { apiClient, unwrap } from "./client.ts";
import { mockDelay } from "../mocks/delay.ts";
import {
  mockApproveKakaoPayment,
  mockCancelOrder,
  mockCreateOrder,
  mockGetMyOrders,
  mockGetOrder,
  mockGetOrderDelivery,
  mockRefundOrder,
  mockRequestOrderReturn,
} from "../mocks/orders.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// POST /api/orders — 주문 생성. request 형태는 CreateOrderRequest(api/types.ts) 참고.
// VIRTUAL_CARD는 이 호출 안에서 결제까지 끝나 orderStatus=PAID로 응답한다. KAKAO_PAY는
// 결제 준비(Ready)만 끝나 orderStatus=PENDING_PAYMENT + nextRedirectUrl로 응답하며,
// 최종 승인은 approveKakaoPayment()가 담당한다.
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

// GET /api/orders — 내 주문 목록 (페이징, id 내림차순). 품목/결제 상세는 없다 —
// 상세가 필요하면 getOrder()를 따로 호출한다.
export async function getMyOrders({ page = 0, size = 20 } = {}) {
  if (USE_MOCK) return mockDelay(mockGetMyOrders({ page, size }));
  return unwrap(apiClient.get("/orders", { params: { page, size } }));
}

// GET /api/orders/{orderId} — 주문 상세(품목/받는분/결제내역).
export async function getOrder(orderId) {
  if (USE_MOCK) {
    const order = mockGetOrder(orderId);
    if (!order) throw new Error("주문을 찾을 수 없습니다.");
    return mockDelay(order);
  }
  return unwrap(apiClient.get(`/orders/${orderId}`));
}

// POST /api/orders/{orderId}/cancel — 주문 취소. PAID 상태만 가능(409).
export async function cancelOrder(orderId) {
  if (USE_MOCK) {
    const order = mockCancelOrder(orderId);
    if (!order) throw new Error("주문을 찾을 수 없습니다.");
    return mockDelay(order);
  }
  return unwrap(apiClient.post(`/orders/${orderId}/cancel`));
}

// POST /api/orders/{orderId}/return — 반품/교환 신청. PAID 상태만 가능(409), reason 필수.
export async function requestOrderReturn(orderId, reason) {
  if (USE_MOCK) {
    const order = mockRequestOrderReturn(orderId, reason);
    if (!order) throw new Error("주문을 찾을 수 없습니다.");
    return mockDelay(order);
  }
  return unwrap(apiClient.post(`/orders/${orderId}/return`, { reason }));
}

// POST /api/orders/{orderId}/refund — 환불 처리. RETURN_REQUESTED 상태만 가능(409).
// 이번 스코프의 OrdersTab UI에서는 호출하지 않는다(반품 접수 이후 처리 흐름은 별도 논의 대상).
export async function refundOrder(orderId) {
  if (USE_MOCK) {
    const order = mockRefundOrder(orderId);
    if (!order) throw new Error("주문을 찾을 수 없습니다.");
    return mockDelay(order);
  }
  return unwrap(apiClient.post(`/orders/${orderId}/refund`));
}

// GET /api/orders/{orderId}/delivery — 배송 상태 조회. PENDING_PAYMENT 상태 주문은
// 배송 레코드가 아직 없어 404다(OrderService.createDelivery가 결제 확정 시점에만 생성).
export async function getOrderDelivery(orderId) {
  if (USE_MOCK) {
    const delivery = mockGetOrderDelivery(orderId);
    if (!delivery) throw new Error("배송 정보를 찾을 수 없습니다.");
    return mockDelay(delivery);
  }
  return unwrap(apiClient.get(`/orders/${orderId}/delivery`));
}
