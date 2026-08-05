// UI 전용 회원 타입. 백엔드 DTO(src/api/types.ts)와 분리한다.

/** 구매 실적 기반 회원 등급. 구독 여부와 무관하며, 구독 없이도 구매로 승급 가능. BASIC이 가입 시 기본값. */
export type MemberGrade = 'BASIC' | 'PREMIUM'

/** 회원 등급 + 포인트 (GET /api/members/me/grade) */
export interface GradeInfo {
  grade: MemberGrade
  point: number
  /** 등급이 PREMIUM인지 여부. 웹툰 요약 컷 등 구독 전용 기능 접근권과는 무관(그건 Subscription 기준). */
  isPremium: boolean
}

/** 내 정보 (GET /api/members/me) */
export interface Member {
  id: string
  name: string
  email: string
  grade: MemberGrade
  point: number
}

/** 구독 여부 (GET /api/members/me/subscription). 구독 이력이 없으면 비구독으로 취급한다. */
export interface Subscription {
  isActive: boolean
}
