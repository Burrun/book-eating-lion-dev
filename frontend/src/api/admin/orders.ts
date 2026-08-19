import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import { mockGetAdminOrders, mockUpdateDeliveryStatus } from "../../mocks/admin/orders.ts";
import type {
  AdminOrderSummaryResponse,
  ApiResponse,
  DeliveryResponse,
  DeliveryStatus,
  OrderStatus,
  Page,
} from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/orders/admin — 관리자용 전체 주문 목록 (배송상태 포함, id 내림차순)
export async function getAdminOrders(
  params: {
    status?: OrderStatus;
    page?: number;
    size?: number;
  } = {},
): Promise<Page<AdminOrderSummaryResponse>> {
  if (USE_MOCK) return mockDelay(mockGetAdminOrders(params));
  return unwrap(
    apiClient.get<ApiResponse<Page<AdminOrderSummaryResponse>>>("/orders/admin", { params }),
  );
}

// PATCH /api/orders/admin/{orderId}/delivery-status — 배송 상태를 다음 단계로 변경.
// PENDING→SHIPPED→IN_TRANSIT→DELIVERED 순서로 한 단계씩만 허용된다(서버가 강제, 어겨도 409).
export async function updateOrderDeliveryStatus(
  orderId: number | string,
  status: DeliveryStatus,
): Promise<DeliveryResponse> {
  if (USE_MOCK) {
    const updated = mockUpdateDeliveryStatus(orderId, status);
    if (!updated) throw new Error("배송 정보를 찾을 수 없습니다.");
    return mockDelay(updated);
  }
  return unwrap(
    apiClient.patch<ApiResponse<DeliveryResponse>>(`/orders/admin/${orderId}/delivery-status`, {
      status,
    }),
  );
}
