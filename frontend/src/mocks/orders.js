// 주문 생성 목업. 실제 order-service 응답 상세 스펙이 아직 로컬에서 확인 안 돼서
// orderId/status만 최소한으로 흉내낸다.
let nextOrderId = 1001;

export function mockCreateOrder(request) {
  // 무통장입금은 입금 확인 전이라 대기 상태, 카드는 즉시 승인되는 것으로 가정.
  const status = request.paymentMethod === "BANK_TRANSFER" ? "PENDING" : "PAID";
  return {
    orderId: nextOrderId++,
    status,
  };
}
