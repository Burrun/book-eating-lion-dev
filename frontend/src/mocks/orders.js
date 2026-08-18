// 주문 생성/카카오페이 승인 목업. 실 API 응답 구조(OrderResponse, api/types.ts)를 그대로 맞춰서
// Checkout.jsx/KakaoPayCallback.jsx가 mock/실API 모드에서 동일하게 동작하게 한다.
let nextOrderId = 1001;

// 실 API 모드에서 order-service가 계산해주는 값(도서 가격 재조회)을 흉내내는 임시 단가.
const MOCK_UNIT_PRICE = 10000;

function buildItems(requestItems) {
  return requestItems.map((item, i) => ({
    orderItemId: i + 1,
    bookId: item.bookId,
    bookTitle: `mock book ${item.bookId}`,
    quantity: item.quantity,
    unitPrice: MOCK_UNIT_PRICE,
  }));
}

export function mockCreateOrder(request) {
  const orderId = nextOrderId++;
  const items = buildItems(request.items);
  const totalAmount = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);

  if (request.paymentMethod === "KAKAO_PAY") {
    return {
      orderId,
      orderStatus: "PENDING_PAYMENT",
      recipient: request.recipient,
      totalAmount,
      items,
      payment: {
        paymentId: orderId,
        paymentMethod: "KAKAO_PAY",
        amount: totalAmount,
        paymentStatus: "READY",
        approvalNumber: null,
        pgTid: `mock-tid-${orderId}`,
      },
      // 실제로는 카카오 결제 페이지 URL이지만, mock 모드는 실 sandbox 키가 없으므로
      // 우리 콜백 라우트로 곧장 되돌아오게 해 리다이렉트→승인 흐름 전체를 브라우저로 확인할 수 있게 한다.
      nextRedirectUrl: `/payments/kakao/callback?orderId=${orderId}&pg_token=mock-pg-token`,
      returnReason: null,
    };
  }

  return {
    orderId,
    orderStatus: "PAID",
    recipient: request.recipient,
    totalAmount,
    items,
    payment: {
      paymentId: orderId,
      paymentMethod: request.paymentMethod,
      amount: totalAmount,
      paymentStatus: "APPROVED",
      approvalNumber: `mock-approval-${orderId}`,
      pgTid: null,
    },
    nextRedirectUrl: null,
    returnReason: null,
  };
}

export function mockApproveKakaoPayment(orderId) {
  return {
    orderId: Number(orderId),
    orderStatus: "PAID",
    totalAmount: 0,
    items: [],
    payment: {
      paymentId: Number(orderId),
      paymentMethod: "KAKAO_PAY",
      amount: 0,
      paymentStatus: "APPROVED",
      approvalNumber: `mock-approval-${orderId}`,
      pgTid: `mock-tid-${orderId}`,
    },
    nextRedirectUrl: null,
    returnReason: null,
  };
}
