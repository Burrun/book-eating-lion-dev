import { apiClient, unwrap } from "./client.ts";
import type { ApiResponse, ReadingProgressResponse } from "./types.ts";

// GET /api/catalog/books/{bookId}/reading-progress — 전자책 이어읽기 위치 조회 (JWT 인증 필요)
// 한 번도 읽지 않은 책은 에러가 아니라 정상 상태라 data: null 이 그대로 내려온다.
export async function getReadingProgress(
  bookId: number | string,
): Promise<ReadingProgressResponse | null> {
  return unwrap(
    apiClient.get<ApiResponse<ReadingProgressResponse | null>>(
      `/catalog/books/${bookId}/reading-progress`,
    ),
  );
}

// PUT /api/catalog/books/{bookId}/reading-progress — 회원×도서 단위 upsert (JWT 인증 필요)
export async function saveReadingProgress(
  bookId: number | string,
  cfi: string,
  percentage?: number,
): Promise<ReadingProgressResponse> {
  return unwrap(
    apiClient.put<ApiResponse<ReadingProgressResponse>>(
      `/catalog/books/${bookId}/reading-progress`,
      { cfi, percentage },
    ),
  );
}
