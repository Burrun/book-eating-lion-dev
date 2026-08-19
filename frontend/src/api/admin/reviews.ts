import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import { mockGetReviews } from "../../mocks/reviews.ts";
import type { ApiResponse, Page, ReviewResponse } from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/admin/books/{bookId}/reviews — 도서별 리뷰 열람 (조회 전용, 검증/삭제 API는 백엔드에 없음)
export async function getAdminBookReviews(
  bookId: number | string,
  params: { page?: number; size?: number } = {},
): Promise<Page<ReviewResponse>> {
  if (USE_MOCK) return mockDelay(mockGetReviews(bookId, params.page, params.size));
  return unwrap(
    apiClient.get<ApiResponse<Page<ReviewResponse>>>(`/catalog/admin/books/${bookId}/reviews`, {
      params,
    }),
  );
}
