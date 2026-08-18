import { apiClient, unwrap } from "./client.ts";
import { toMember, toSubscription } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import { MOCK_PROFILE } from "../mocks/mypage.js";
import type {
  ApiResponse,
  MemberResponse,
  SubscribeRequest,
  SubscriptionResponse,
} from "./types.ts";
import type { Member, Subscription } from "../types/member.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/members/me — 내 정보 조회 (JWT 인증 필요)
export async function getMyProfile(): Promise<Member> {
  if (USE_MOCK) return mockDelay(toMember(MOCK_PROFILE as MemberResponse));
  return toMember(await unwrap(apiClient.get<ApiResponse<MemberResponse>>("/members/me")));
}

// GET /api/members/me/subscription — 구독 상태 조회 (JWT 인증 필요, 구독 이력 없으면 data: null)
export async function getMySubscription(): Promise<Subscription> {
  return toSubscription(
    await unwrap(
      apiClient.get<ApiResponse<SubscriptionResponse | null>>("/members/me/subscription"),
    ),
  );
}

// POST /api/members/me/subscription — 구독 시작 (JWT 인증 필요).
// 결제 미연동 스코프: 본인 호출로 즉시 활성화된다. 이미 활성 구독이 있으면 409.
export async function subscribe(planType: SubscribeRequest["planType"]): Promise<Subscription> {
  return toSubscription(
    await unwrap(
      apiClient.post<ApiResponse<SubscriptionResponse>>("/members/me/subscription", { planType }),
    ),
  );
}

// DELETE /api/members/me/subscription — 구독 해지 (JWT 인증 필요)
export async function cancelSubscription(): Promise<Subscription> {
  return toSubscription(
    await unwrap(apiClient.delete<ApiResponse<SubscriptionResponse>>("/members/me/subscription")),
  );
}
