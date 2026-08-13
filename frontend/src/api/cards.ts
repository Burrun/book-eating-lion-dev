import { apiClient, unwrap } from "./client.ts";
import { toCard } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockGetCards, mockIssueCard, mockUpdateCardStatus } from "../mocks/cards.ts";
import type { ApiResponse, CardIssueRequest, CardResponse, CardUpdateRequest } from "./types.ts";
import type { Card } from "../types/card.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/cards/me — 내 가상카드 목록
export async function getMyCards(): Promise<Card[]> {
  if (USE_MOCK) return mockDelay(mockGetCards().map(toCard));
  const list = await unwrap(apiClient.get<ApiResponse<CardResponse[]>>("/cards/me"));
  return list.map(toCard);
}

// POST /api/cards — 가상카드 발급 (body 자체도 생략 가능)
export async function issueCard(body: CardIssueRequest = {}): Promise<Card> {
  if (USE_MOCK) return mockDelay(toCard(mockIssueCard(body)));
  return toCard(await unwrap(apiClient.post<ApiResponse<CardResponse>>("/cards", body)));
}

// PATCH /api/cards/{cardId}/status — 카드 상태 변경 (월 한도 변경 API는 백엔드에 없음)
export async function updateCardStatus(
  cardId: number | string,
  body: CardUpdateRequest,
): Promise<Card> {
  if (USE_MOCK) {
    const updated = mockUpdateCardStatus(cardId, body.cardStatus);
    if (!updated) throw new Error("카드를 찾을 수 없습니다.");
    return mockDelay(toCard(updated));
  }
  return toCard(
    await unwrap(apiClient.patch<ApiResponse<CardResponse>>(`/cards/${cardId}/status`, body)),
  );
}
