import { apiClient, unwrap } from "./client.ts";
import { toBookSummary } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockAddToWishlist, mockGetWishlist, mockRemoveFromWishlist } from "../mocks/wishlist.ts";
import type { ApiResponse, BookSummaryResponse } from "./types.ts";
import type { BookSummary } from "../types/book.ts";

// 로그인 연동(BOO-23) 전까지는 목업으로 화면을 확인한다.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/wishlist/me — 찜 목록 (JWT 인증 + X-Member-Id 필요)
// 찜 목록은 catalog-service 소유 데이터라 /api/catalog/** 네임스페이스로 라우팅된다.
export async function getWishlist(): Promise<BookSummary[]> {
  if (USE_MOCK) return mockDelay(mockGetWishlist().map(toBookSummary));
  const list = await unwrap(
    apiClient.get<ApiResponse<BookSummaryResponse[]>>("/catalog/wishlist/me"),
  );
  return list.map(toBookSummary);
}

// POST /api/catalog/wishlist/{bookId} — 찜 추가 (멱등, X-Member-Id 필요)
export async function addToWishlist(bookId: number | string): Promise<void> {
  if (USE_MOCK) {
    mockAddToWishlist(bookId);
    return mockDelay(undefined);
  }
  await unwrap(apiClient.post<ApiResponse<void>>(`/catalog/wishlist/${bookId}`));
}

// DELETE /api/catalog/wishlist/{bookId} — 찜 삭제 (멱등, X-Member-Id 필요)
export async function removeFromWishlist(bookId: number | string): Promise<void> {
  if (USE_MOCK) {
    mockRemoveFromWishlist(bookId);
    return mockDelay(undefined);
  }
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/wishlist/${bookId}`));
}

// GET /api/catalog/members/me/recent-books — 최근 본 상품 (JWT 인증 필요)
// 회원별 데이터지만 catalog-service가 소유하고 있어 /api/catalog/** 네임스페이스에 속한다.
export async function getRecentBooks(limit = 20): Promise<BookSummary[]> {
  const list = await unwrap(
    apiClient.get<ApiResponse<BookSummaryResponse[]>>("/catalog/members/me/recent-books", {
      params: { limit },
    }),
  );
  return list.map(toBookSummary);
}
