import { apiClient, unwrap } from './client.js'
import { toBookSummary } from './mappers.js'
import type { ApiResponse, BookSummaryResponse } from './types.js'
import type { BookSummary } from '../types/book.js'

// GET /api/members/me/wishlist — 찜 목록 (JWT 인증 + X-Member-Id 필요)
export async function getWishlist(): Promise<BookSummary[]> {
  const list = await unwrap(apiClient.get<ApiResponse<BookSummaryResponse[]>>('/members/me/wishlist'))
  return list.map(toBookSummary)
}

// POST /api/wishlist/{bookId} — 찜 추가 (멱등, X-Member-Id 필요)
export async function addToWishlist(bookId: number | string): Promise<void> {
  await unwrap(apiClient.post<ApiResponse<void>>(`/wishlist/${bookId}`))
}

// DELETE /api/wishlist/{bookId} — 찜 삭제 (멱등, X-Member-Id 필요)
export async function removeFromWishlist(bookId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/wishlist/${bookId}`))
}

// GET /api/members/me/recent-books — 최근 본 상품 (JWT 인증 + X-Member-Id 필요)
export async function getRecentBooks(limit = 20): Promise<BookSummary[]> {
  const list = await unwrap(
    apiClient.get<ApiResponse<BookSummaryResponse[]>>('/members/me/recent-books', { params: { limit } }),
  )
  return list.map(toBookSummary)
}
