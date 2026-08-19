import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import {
  mockCreateCoupon,
  mockGetAdminCoupons,
  mockUpdateCoupon,
} from "../../mocks/admin/coupons.ts";
import type {
  ApiResponse,
  CouponCreateRequest,
  CouponResponse,
  CouponUpdateRequest,
} from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/coupons/admin — 관리자용 전체 쿠폰 목록 (만료일 내림차순)
export async function getAdminCoupons(): Promise<CouponResponse[]> {
  if (USE_MOCK) return mockDelay(mockGetAdminCoupons());
  return unwrap(apiClient.get<ApiResponse<CouponResponse[]>>("/coupons/admin"));
}

// POST /api/coupons/admin — 쿠폰 등록 (쿠폰코드/이름/할인금액/최소주문금액/만료일)
export async function createCoupon(body: CouponCreateRequest): Promise<CouponResponse> {
  if (USE_MOCK) return mockDelay(mockCreateCoupon(body));
  return unwrap(apiClient.post<ApiResponse<CouponResponse>>("/coupons/admin", body));
}

// PATCH /api/coupons/admin/{couponId} — 쿠폰 정책 수정. couponCode는 불변이라 전송하지 않는다.
// 만료일을 과거로 넣는 것이 쿠폰을 종료하는 유일한 방법이다(삭제 API는 없음).
export async function updateCoupon(
  couponId: number | string,
  body: CouponUpdateRequest,
): Promise<CouponResponse> {
  if (USE_MOCK) {
    const updated = mockUpdateCoupon(couponId, body);
    if (!updated) throw new Error("쿠폰을 찾을 수 없습니다.");
    return mockDelay(updated);
  }
  return unwrap(apiClient.patch<ApiResponse<CouponResponse>>(`/coupons/admin/${couponId}`, body));
}
