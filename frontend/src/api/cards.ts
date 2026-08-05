import { apiClient, unwrap } from "./client.ts";
import { toCard } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockGetCards, mockIssueCard, mockUpdateCard } from "../mocks/cards.ts";
import type { ApiResponse, CardIssueRequest, CardResponse, CardUpdateRequest } from "./types.ts";
import type { Card } from "../types/card.ts";

// 백엔드에 card 모듈이 아직 없어 기본은 목업으로 동작한다.
// 엔드포인트 경로는 API 명세서의 기존 컨벤션에서 도출한 것이라 확정 시 조정이 필요하다.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/members/me/cards — 내 가상카드 목록
export async function getMyCards(): Promise<Card[]> {
  if (USE_MOCK) return mockDelay(mockGetCards().map(toCard));
  const list = await unwrap(apiClient.get<ApiResponse<CardResponse[]>>("/members/me/cards"));
  return list.map(toCard);
}

// POST /api/members/me/cards — 가상카드 발급
export async function issueCard(body: CardIssueRequest): Promise<Card> {
  if (USE_MOCK) return mockDelay(toCard(mockIssueCard(body)));
  return toCard(await unwrap(apiClient.post<ApiResponse<CardResponse>>("/members/me/cards", body)));
}

// PATCH /api/cards/{cardId} — 월 한도 변경 / 카드 상태 변경
export async function updateCard(cardId: number | string, body: CardUpdateRequest): Promise<Card> {
  if (USE_MOCK) {
    const updated = mockUpdateCard(cardId, body);
    if (!updated) throw new Error("카드를 찾을 수 없습니다.");
    return mockDelay(toCard(updated));
  }
  return toCard(await unwrap(apiClient.patch<ApiResponse<CardResponse>>(`/cards/${cardId}`, body)));
}
