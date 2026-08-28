import { apiClient, unwrap } from "./client.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockGetFeedableBooks, mockMarkBookFed } from "../mocks/readingProgress.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export interface FeedableBook {
  bookId: number;
  bookTitle: string;
  coverImageUrl: string | null;
  percentage: number | null;
}

// GET /api/catalog/members/me/books/feedable — 완독했지만 아직 사자에게 안 먹인 책.
// 완독 기준(퍼센트)은 서버 설정(catalog.reading.completion-percentage)이 정한다 —
// 프론트는 임계값을 몰라도 된다.
export async function getFeedableBooks(): Promise<FeedableBook[]> {
  if (USE_MOCK) return mockDelay(mockGetFeedableBooks());
  return unwrap(apiClient.get("/catalog/members/me/books/feedable"));
}

// PATCH /api/catalog/books/{bookId}/reading-progress/fed — POST /api/ai/lion/feed 성공 직후.
// 실제 EXP/사자 상태는 ai-service 소유고, 여기 fedAt은 "먹일 수 있는 책" 목록을 거르기 위한
// 로컬 표시일 뿐이다.
export async function markBookFed(bookId: number | string): Promise<void> {
  if (USE_MOCK) return mockDelay(mockMarkBookFed(bookId));
  await apiClient.patch(`/catalog/books/${bookId}/reading-progress/fed`);
}
