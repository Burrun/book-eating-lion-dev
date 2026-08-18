import { apiClient, unwrap } from "./client.ts";
import { toPaged, toReview } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import {
  mockCreateReview,
  mockDeleteReview,
  mockGetReviews,
  mockUpdateReview,
} from "../mocks/reviews.ts";
import type {
  ApiResponse,
  Page,
  ReviewRequest,
  ReviewResponse,
  ReviewUpdateRequest,
} from "./types.ts";
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
// 구매 확정 시 발급된 review_permissions 가 있어야 통과한다. 없으면 403
// REVIEW_PERMISSION_REQUIRED(unwrap이 Error.code로 실어 던진다).
export async function createReview(bookId: number | string, body: ReviewRequest): Promise<Review> {
  if (USE_MOCK) return toReview(await mockDelay(mockCreateReview(bookId, body)));
  return toReview(
    await unwrap(
      apiClient.post<ApiResponse<ReviewResponse>>(`/catalog/books/${bookId}/reviews`, body),
    ),
  );
}

// PATCH /api/catalog/reviews/{reviewId} — 내 리뷰 수정 (작성자 본인만, 로그인 필요)
export async function updateReview(
  reviewId: number | string,
  body: ReviewUpdateRequest,
): Promise<Review> {
  if (USE_MOCK) {
    const updated = await mockDelay(mockUpdateReview(reviewId, body));
    if (!updated) throw new Error("리뷰를 찾을 수 없습니다.");
    return toReview(updated);
  }
  return toReview(
    await unwrap(
      apiClient.patch<ApiResponse<ReviewResponse>>(`/catalog/reviews/${reviewId}`, body),
    ),
  );
}

// DELETE /api/catalog/reviews/{reviewId} — 리뷰 삭제 (작성자 본인만, 로그인 필요)
export async function deleteReview(reviewId: number | string): Promise<void> {
  if (USE_MOCK) {
    await mockDelay(mockDeleteReview(reviewId));
    return;
  }
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/reviews/${reviewId}`));
}
