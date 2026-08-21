// UI 전용 회원 타입. 백엔드 DTO(src/api/types.ts)와 분리한다.

/** 내 정보 (GET /api/members/me) */
export interface Member {
  id: string;
  name: string;
  email: string;
  role: "USER" | "ADMIN";
}

/** 구독 여부 (GET /api/members/me/subscription). 구독 이력이 없으면 비구독으로 취급한다. */
export interface Subscription {
  isActive: boolean;
  // 구독 이력이 없으면(dto가 null) 전부 null — isActive만으로 배지/버튼을 결정하는
  // 기존 화면(ProductListPage/ProductDetailPage)은 이 필드들을 쓰지 않는다.
  planType: "MONTHLY" | "YEARLY" | null;
  status: "ACTIVE" | "CANCELLED" | "EXPIRED" | null;
  startedAt: string | null;
  expiresAt: string | null;
}
