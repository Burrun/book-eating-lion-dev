import { apiClient, unwrap } from "./client.ts";
import { toPaged, toReview } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockGetReviews } from "../mocks/reviews.ts";
import type { ApiResponse, Page, ReviewRequest, ReviewResponse } from "./types.ts";
import type { Review } from "../types/book.ts";
import type { Paged } from "../types/common.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/books/{bookId}/reviews — 리뷰 목록
export async function getReviews(
  bookId: number | string,
  params: { page?: number; size?: number } = {},
): Promise<Paged<Review>> {
  const page = USE_MOCK
    ? await mockDelay(mockGetReviews(bookId, params.page, params.size))
    : await unwrap(
        apiClient.get<ApiResponse<Page<ReviewResponse>>>(`/catalog/books/${bookId}/reviews`, {
          params,
        }),
      );
  return toPaged(page, toReview);
}

// POST /api/catalog/books/{bookId}/reviews — 리뷰 작성 (로그인 필요)
// 구매 확정 시 발급된 review_permissions 가 있어야 통과한다.
export async function createReview(bookId: number | string, body: ReviewRequest): Promise<Review> {
  return toReview(
    await unwrap(
      apiClient.post<ApiResponse<ReviewResponse>>(`/catalog/books/${bookId}/reviews`, body),
    ),
  );
}

// DELETE /api/catalog/reviews/{reviewId} — 리뷰 삭제 (작성자 본인만, 로그인 필요)
export async function deleteReview(reviewId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/reviews/${reviewId}`));
}
