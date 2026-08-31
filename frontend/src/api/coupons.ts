import { apiClient, unwrap } from "./client.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockRegisterCoupon } from "../mocks/mypage.js";
import type { ApiResponse, MemberCouponView } from "./types.ts";

// 보유 쿠폰 목록(GET /api/coupons/me)은 api/mypage.js 의 fetchCoupons 가 이미 담당한다 —
// 여기서는 일반 사용자의 "쿠폰 코드 등록"만 다룬다.

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

/**
 * POST /api/coupons/register — 쿠폰 코드를 입력해 내 계정에 쿠폰을 발급받는다.
 *
 * 실패는 백엔드 CouponErrorCode 를 그대로 받는다:
 *  - 404 COUPON_NOT_FOUND      존재하지 않는 코드
 *  - 400 COUPON_EXPIRED        만료된 쿠폰
 *  - 409 COUPON_ALREADY_ISSUED 이미 보유
 * 호출부는 err.response?.data?.error?.message 를 그대로 보여주면 된다.
 */
export async function registerCoupon(code: string): Promise<MemberCouponView> {
  if (USE_MOCK) return mockDelay(mockRegisterCoupon(code));
  return unwrap(apiClient.post<ApiResponse<MemberCouponView>>("/coupons/register", { code }));
}
