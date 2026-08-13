// UI 전용 회원 타입. 백엔드 DTO(src/api/types.ts)와 분리한다.

/** 내 정보 (GET /api/members/me) */
export interface Member {
  id: string;
  name: string;
  email: string;
}

/** 구독 여부 (GET /api/members/me/subscription). 구독 이력이 없으면 비구독으로 취급한다. */
export interface Subscription {
  isActive: boolean;
}
