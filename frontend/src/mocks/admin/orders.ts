import type { AdminOrderSummaryResponse, DeliveryResponse, DeliveryStatus, OrderStatus } from "../../api/types.ts";
import type { Page } from "../../api/types.ts";

const NEXT_STATUS: Record<DeliveryStatus, DeliveryStatus | null> = {
  PENDING: "SHIPPED",
  SHIPPED: "IN_TRANSIT",
  IN_TRANSIT: "DELIVERED",
  DELIVERED: null,
};

let orders: AdminOrderSummaryResponse[] = [
  {
    orderId: 101,
    memberId: "9f8c1a2b-3d4e-5f60-7a8b-9c0d1e2f3a4b",
    recipientName: "홍길동",
    orderStatus: "PAID",
    deliveryStatus: "PENDING",
    totalAmount: 32000,
  },
  {
    orderId: 102,
    memberId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    recipientName: "김철수",
    orderStatus: "PAID",
    deliveryStatus: "SHIPPED",
    totalAmount: 18000,
  },
  {
    orderId: 103,
    memberId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    recipientName: "김철수",
    orderStatus: "PAID",
    deliveryStatus: "DELIVERED",
    totalAmount: 45000,
  },
  {
    orderId: 104,
    memberId: "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    recipientName: "이영희",
    orderStatus: "PENDING_PAYMENT",
    deliveryStatus: undefined,
    totalAmount: 27000,
  },
];

let deliveries: DeliveryResponse[] = [
  { id: 1, orderId: 101, deliveryStatus: "PENDING", createdAt: "2026-08-15T10:00:00", updatedAt: "2026-08-15T10:00:00" },
  { id: 2, orderId: 102, courierCompany: "CJ대한통운", trackingNumber: "123456789", deliveryStatus: "SHIPPED", createdAt: "2026-08-14T09:00:00", updatedAt: "2026-08-16T11:00:00" },
  { id: 3, orderId: 103, courierCompany: "우체국택배", trackingNumber: "987654321", deliveryStatus: "DELIVERED", createdAt: "2026-08-10T09:00:00", updatedAt: "2026-08-13T15:00:00" },
];

export function mockGetAdminOrders(params: {
  status?: OrderStatus;
  page?: number;
  size?: number;
}): Page<AdminOrderSummaryResponse> {
  const { status, page = 0, size = 20 } = params;
  const matched = orders
    .filter((o) => status == null || o.orderStatus === status)
    .sort((a, b) => b.orderId - a.orderId);
  const totalPages = Math.max(1, Math.ceil(matched.length / size));
  const number = Math.min(Math.max(page, 0), totalPages - 1);
  return {
    content: matched.slice(number * size, number * size + size),
    number,
    size,
    totalElements: matched.length,
    totalPages,
    first: number === 0,
    last: number === totalPages - 1,
  };
}

export function mockUpdateDeliveryStatus(
  orderId: number | string,
  status: DeliveryStatus,
): DeliveryResponse | undefined {
  const delivery = deliveries.find((d) => String(d.orderId) === String(orderId));
  if (!delivery) return undefined;

  const current = delivery.deliveryStatus as DeliveryStatus;
  if (NEXT_STATUS[current] !== status) {
    throw new Error(`배송 상태를 전환할 수 없습니다: current=${current}, requested=${status}`);
  }

  let updated: DeliveryResponse | undefined;
  deliveries = deliveries.map((d) => {
    if (String(d.orderId) !== String(orderId)) return d;
    updated = { ...d, deliveryStatus: status, updatedAt: new Date().toISOString() };
    return updated;
  });
  orders = orders.map((o) => (o.orderId === Number(orderId) ? { ...o, deliveryStatus: status } : o));

  return updated;
}
