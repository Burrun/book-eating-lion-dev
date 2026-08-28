import { apiClient, unwrap } from "./client.ts";
import { mockDelay } from "../mocks/delay.ts";
import {
  mockCreateHighlight,
  mockDeleteHighlight,
  mockGetMyHighlights,
} from "../mocks/bookHighlight.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export interface BookHighlight {
  highlightId: number;
  bookId: number;
  bookTitle: string;
  cfiRange: string;
  selectedText: string;
  memoText: string | null;
  createdAt: string;
}

export interface BookHighlightInput {
  cfiRange: string;
  selectedText: string;
  memoText: string;
}

// POST /api/catalog/books/{bookId}/highlights — 뷰어에서 긁은 문장 + 메모 저장.
// 책당 여러 개가 쌓인다(예전 완독 요약 메모처럼 1개 upsert가 아니다).
export async function createHighlight(
  bookId: number | string,
  input: BookHighlightInput,
): Promise<BookHighlight> {
  if (USE_MOCK) return mockDelay(mockCreateHighlight(bookId, input));
  return unwrap(apiClient.post(`/catalog/books/${bookId}/highlights`, input));
}

// GET /api/catalog/members/me/highlights — 마이페이지 "내 메모" 목록(최신순, 책 구분 없이 전부).
export async function getMyHighlights(): Promise<BookHighlight[]> {
  if (USE_MOCK) return mockDelay(mockGetMyHighlights());
  return unwrap(apiClient.get("/catalog/members/me/highlights"));
}

// DELETE /api/catalog/highlights/{highlightId} — 남의 메모는 404다.
export async function deleteHighlight(highlightId: number): Promise<void> {
  if (USE_MOCK) return mockDelay(mockDeleteHighlight(highlightId));
  await apiClient.delete(`/catalog/highlights/${highlightId}`);
}
