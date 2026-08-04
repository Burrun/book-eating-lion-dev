import { apiClient, unwrap } from './client.js'
import { toGradeInfo, toMember } from './mappers.js'
import type { ApiResponse, MemberGradeResponse, MemberResponse } from './types.js'
import type { GradeInfo, Member } from '../types/member.js'

// GET /api/members/me — 내 정보 조회 (JWT 인증 필요)
export async function getMyProfile(): Promise<Member> {
  return toMember(await unwrap(apiClient.get<ApiResponse<MemberResponse>>('/members/me')))
}

// GET /api/members/me/grade — 회원 등급 및 포인트 조회 (JWT 인증 필요)
export async function getMyGrade(): Promise<GradeInfo> {
  return toGradeInfo(await unwrap(apiClient.get<ApiResponse<MemberGradeResponse>>('/members/me/grade')))
}
