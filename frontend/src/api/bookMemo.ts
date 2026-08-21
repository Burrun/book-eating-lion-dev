import { apiClient, unwrap } from "./client.ts";
import { mockDelay } from "../mocks/delay.ts";
import {
  mockGetBookMemo,
  mockSaveBookMemo,
  mockGetFeedableMemos,
  mockGetFedMemos,
  mockMarkMemoFed,
} from "../mocks/bookMemo.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export interface BookMemo {
  bookId: string;
  memoText: string;
  fedAt: string | null;
  updatedAt: string;
}

export interface FeedableMemo {
  bookId: string;
  bookTitle: string;
  memoText: string;
}

export interface FedMemo {
  bookId: string;
  bookTitle: string;
  memoText: string;
}

// PUT /api/catalog/books/{bookId}/memo — 완독 요약 메모 저장(upsert, 책당 1개).
export async function saveBookMemo(bookId: string, memoText: string): Promise<BookMemo> {
  if (USE_MOCK) return mockDelay(mockSaveBookMemo(bookId, memoText));
  return unwrap(apiClient.put(`/catalog/books/${bookId}/memo`, { memoText }));
}

// GET /api/catalog/books/{bookId}/memo — 내 메모 조회. 아직 안 썼으면 data: null.
export async function getBookMemo(bookId: string): Promise<BookMemo | null> {
  if (USE_MOCK) return mockDelay(mockGetBookMemo(bookId));
  return unwrap(apiClient.get(`/catalog/books/${bookId}/memo`));
}

// GET /api/catalog/members/me/memos/feedable — 아직 사자에게 안 먹인 내 메모 목록.
// LionFeedingCard가 드래그 카드로 그린다.
export async function getFeedableMemos(): Promise<FeedableMemo[]> {
  if (USE_MOCK) return mockDelay(mockGetFeedableMemos());
  return unwrap(apiClient.get("/catalog/members/me/memos/feedable"));
}

// GET /api/catalog/members/me/memos/fed — 이미 사자에게 먹인 내 메모 목록.
// "사자에게 물어보기" 패널의 "내가 먹인 요약 메모" 목록이 그린다.
export async function getFedMemos(): Promise<FedMemo[]> {
  if (USE_MOCK) return mockDelay(mockGetFedMemos());
  return unwrap(apiClient.get("/catalog/members/me/memos/fed"));
}

// PATCH /api/catalog/books/{bookId}/memo/fed — POST /api/ai/lion/feed 성공 직후 호출한다.
// 실제 EXP/인덱싱 상태는 ai-service 소유고, 여기 fedAt은 "먹일 수 있는 메모" 목록을
// 거르기 위한 로컬 표시일 뿐이다.
export async function markMemoFed(bookId: string): Promise<void> {
  if (USE_MOCK) return mockDelay(mockMarkMemoFed(bookId));
  await apiClient.patch(`/catalog/books/${bookId}/memo/fed`);
}
