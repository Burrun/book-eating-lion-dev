import { apiClient, unwrap } from './client.js'
import { toPaged, toReview } from './mappers.js'
import type { ApiResponse, Page, ReviewRequest, ReviewResponse } from './types.js'
import type { Review } from '../types/book.js'
import type { Paged } from '../types/common.js'

// GET /api/books/{bookId}/reviews — 리뷰 목록
export async function getReviews(
  bookId: number | string,
  params: { page?: number; size?: number } = {},
): Promise<Paged<Review>> {
  const page = await unwrap(
    apiClient.get<ApiResponse<Page<ReviewResponse>>>(`/books/${bookId}/reviews`, { params }),
  )
  return toPaged(page, toReview)
}

// POST /api/books/{bookId}/reviews — 리뷰 작성 (X-Member-Id 필요)
export async function createReview(bookId: number | string, body: ReviewRequest): Promise<Review> {
  return toReview(await unwrap(apiClient.post<ApiResponse<ReviewResponse>>(`/books/${bookId}/reviews`, body)))
}

// DELETE /api/reviews/{reviewId} — 리뷰 삭제 (작성자 본인만, X-Member-Id 필요)
export async function deleteReview(reviewId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/reviews/${reviewId}`))
}
