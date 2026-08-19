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

// --- 마이페이지 주문/배송 조회 목업 ---------------------------------------------------
// mockCreateOrder()가 만드는 주문과는 별개의 독립 시드다. 방금 만든 주문이 이 목록에
// 나타나진 않는다 — 실 API 모드와 달리 mock 모드는 "결제 흐름"과 "조회 흐름"을 각자
// 목적에 맞게 최소로만 흉내낸다.
let orders = [
  {
    orderId: 2001,
    memberId: "mock-member",
    recipientName: "홍길동",
    recipientPhone: "010-0000-0000",
    postalCode: "06236",
    address: "서울시 강남구",
    orderStatus: "PAID",
    totalAmount: 38700,
    items: [{ orderItemId: 1, bookId: 1, bookTitle: "자바 ORM 표준 JPA 프로그래밍", quantity: 1, unitPrice: 38700 }],
    payment: {
      paymentId: 2001,
      paymentMethod: "VIRTUAL_CARD",
      amount: 38700,
      paymentStatus: "APPROVED",
      approvalNumber: "mock-approval-2001",
      pgTid: null,
    },
    returnReason: null,
  },
  {
    orderId: 2002,
    memberId: "mock-member",
    recipientName: "홍길동",
    recipientPhone: "010-0000-0000",
    postalCode: "06236",
    address: "서울시 강남구",
    orderStatus: "RETURN_REQUESTED",
    totalAmount: 29000,
    items: [{ orderItemId: 2, bookId: 2, bookTitle: "클린 코드 (Clean Code)", quantity: 1, unitPrice: 29000 }],
    payment: {
      paymentId: 2002,
      paymentMethod: "KAKAO_PAY",
      amount: 29000,
      paymentStatus: "APPROVED",
      approvalNumber: "mock-approval-2002",
      pgTid: "mock-tid-2002",
    },
    returnReason: "단순 변심",
  },
  {
    orderId: 2003,
    memberId: "mock-member",
    recipientName: "홍길동",
    recipientPhone: "010-0000-0000",
    postalCode: "06236",
    address: "서울시 강남구",
    orderStatus: "CANCELLED",
    totalAmount: 32000,
    items: [{ orderItemId: 3, bookId: 3, bookTitle: "스프링 부트 실전 활용", quantity: 1, unitPrice: 32000 }],
    payment: {
      paymentId: 2003,
      paymentMethod: "VIRTUAL_CARD",
      amount: 32000,
      paymentStatus: "APPROVED",
      approvalNumber: "mock-approval-2003",
      pgTid: null,
    },
    returnReason: null,
  },
  {
    orderId: 2004,
    memberId: "mock-member",
    recipientName: "홍길동",
    recipientPhone: "010-0000-0000",
    postalCode: "06236",
    address: "서울시 강남구",
    orderStatus: "PENDING_PAYMENT",
    totalAmount: 19800,
    items: [{ orderItemId: 4, bookId: 4, bookTitle: "이펙티브 자바", quantity: 1, unitPrice: 19800 }],
    payment: {
      paymentId: 2004,
      paymentMethod: "KAKAO_PAY",
      amount: 19800,
      paymentStatus: "READY",
      approvalNumber: null,
      pgTid: "mock-tid-2004",
    },
    returnReason: null,
  },
];

// orderId=PENDING_PAYMENT(2004)인 주문은 결제 확정 전이라 배송 레코드가 없다 —
// 실 API(OrderService.createDelivery)와 같은 규칙이다.
let deliveries = [
  { id: 1, orderId: 2001, courierCompany: "CJ대한통운", trackingNumber: "123456789", deliveryStatus: "SHIPPED", createdAt: "2026-07-29T10:00:00", updatedAt: "2026-07-30T09:00:00" },
  { id: 2, orderId: 2002, courierCompany: "우체국택배", trackingNumber: "987654321", deliveryStatus: "DELIVERED", createdAt: "2026-07-20T10:00:00", updatedAt: "2026-07-22T14:00:00" },
  { id: 3, orderId: 2003, deliveryStatus: "PENDING", createdAt: "2026-07-15T10:00:00", updatedAt: "2026-07-15T10:00:00" },
];

function toSummary(order) {
  return { orderId: order.orderId, orderStatus: order.orderStatus, totalAmount: order.totalAmount };
}

export function mockGetMyOrders({ page = 0, size = 20 } = {}) {
  const sorted = [...orders].sort((a, b) => b.orderId - a.orderId);
  const totalPages = Math.max(1, Math.ceil(sorted.length / size));
  const number = Math.min(Math.max(page, 0), totalPages - 1);
  return {
    content: sorted.slice(number * size, number * size + size).map(toSummary),
    number,
    size,
    totalElements: sorted.length,
    totalPages,
    first: number === 0,
    last: number === totalPages - 1,
  };
}

export function mockGetOrder(orderId) {
  const order = orders.find((o) => String(o.orderId) === String(orderId));
  if (!order) return undefined;
  return {
    orderId: order.orderId,
    orderStatus: order.orderStatus,
    recipient: {
      name: order.recipientName,
      phone: order.recipientPhone,
      postalCode: order.postalCode,
      address: order.address,
    },
    totalAmount: order.totalAmount,
    items: order.items,
    payment: order.payment,
    nextRedirectUrl: null,
    returnReason: order.returnReason,
  };
}

export function mockCancelOrder(orderId) {
  const order = orders.find((o) => String(o.orderId) === String(orderId));
  if (!order) return undefined;
  if (order.orderStatus !== "PAID") {
    throw new Error("PAID 상태인 주문만 취소할 수 있습니다.");
  }
  order.orderStatus = "CANCELLED";
  return mockGetOrder(orderId);
}

export function mockRequestOrderReturn(orderId, reason) {
  const order = orders.find((o) => String(o.orderId) === String(orderId));
  if (!order) return undefined;
  if (order.orderStatus !== "PAID") {
    throw new Error("PAID 상태인 주문만 반품 신청할 수 있습니다.");
  }
  order.orderStatus = "RETURN_REQUESTED";
  order.returnReason = reason;
  return mockGetOrder(orderId);
}

export function mockRefundOrder(orderId) {
  const order = orders.find((o) => String(o.orderId) === String(orderId));
  if (!order) return undefined;
  if (order.orderStatus !== "RETURN_REQUESTED") {
    throw new Error("RETURN_REQUESTED 상태인 주문만 환불할 수 있습니다.");
  }
  order.orderStatus = "REFUNDED";
  return mockGetOrder(orderId);
}

export function mockGetOrderDelivery(orderId) {
  return deliveries.find((d) => String(d.orderId) === String(orderId));
}
