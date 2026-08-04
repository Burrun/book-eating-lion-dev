// UI 전용 회원 타입. 백엔드 DTO(src/api/types.ts)와 분리한다.

/** 구독 등급. BRONZE는 가입 시 기본값(= 미구독), 나머지는 유료 구독 티어. */
export type MemberGrade = 'BRONZE' | 'SILVER' | 'GOLD' | 'VIP'

/** 구독 등급 + 포인트 (GET /api/members/me/grade) */
export interface GradeInfo {
  grade: MemberGrade
  point: number
  /** 웹툰 요약 컷 등 유료 기능 이용 가능 여부 */
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
