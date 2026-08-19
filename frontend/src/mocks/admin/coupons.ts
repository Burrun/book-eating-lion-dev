import type { CouponCreateRequest, CouponResponse, CouponUpdateRequest } from "../../api/types.ts";

let coupons: CouponResponse[] = [
  {
    couponId: 1,
    couponCode: "WELCOME3000",
    couponName: "신규 가입 3,000원 할인 쿠폰",
    discountAmount: 3000,
    minimumOrderAmount: 10000,
    expiresAt: "2026-12-31T23:59:59",
  },
  {
    couponId: 2,
    couponCode: "SUMMER5000",
    couponName: "여름 독서 페스티벌 할인",
    discountAmount: 5000,
    minimumOrderAmount: 30000,
    expiresAt: "2026-08-15T23:59:59",
  },
];

function nextCouponId(): number {
  return coupons.reduce((max, c) => Math.max(max, c.couponId), 0) + 1;
}

function byExpiresAtDesc(a: CouponResponse, b: CouponResponse): number {
  return b.expiresAt.localeCompare(a.expiresAt);
}

export function mockGetAdminCoupons(): CouponResponse[] {
  return [...coupons].sort(byExpiresAtDesc);
}

export function mockCreateCoupon(body: CouponCreateRequest): CouponResponse {
  if (coupons.some((c) => c.couponCode === body.couponCode)) {
    throw new Error("이미 존재하는 쿠폰 코드입니다.");
  }
  const coupon: CouponResponse = { couponId: nextCouponId(), ...body };
  coupons = [...coupons, coupon];
  return coupon;
}

export function mockUpdateCoupon(
  couponId: number | string,
  body: CouponUpdateRequest,
): CouponResponse | undefined {
  let updated: CouponResponse | undefined;
  coupons = coupons.map((c) => {
    if (String(c.couponId) !== String(couponId)) return c;
    updated = { ...c, ...body };
    return updated;
  });
  return updated;
}
