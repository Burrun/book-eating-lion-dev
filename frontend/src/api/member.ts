import { apiClient, unwrap } from './client.ts'
import { toMember, toSubscription } from './mappers.ts'
import type { ApiResponse, MemberResponse, SubscriptionResponse } from './types.ts'
import type { Member, Subscription } from '../types/member.ts'

// GET /api/members/me — 내 정보 조회 (JWT 인증 필요)
export async function getMyProfile(): Promise<Member> {
  return toMember(await unwrap(apiClient.get<ApiResponse<MemberResponse>>('/members/me')))
}

// GET /api/members/me/subscription — 구독 상태 조회 (JWT 인증 필요, 구독 이력 없으면 data: null)
export async function getMySubscription(): Promise<Subscription> {
  return toSubscription(
    await unwrap(apiClient.get<ApiResponse<SubscriptionResponse | null>>('/members/me/subscription')),
  )
}
