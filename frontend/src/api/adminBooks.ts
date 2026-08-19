import { apiClient, unwrap } from "./client.ts";
import type {
  AdminBookCreateRequest,
  AdminBookResponse,
  AdminBookUpdateRequest,
  ApiResponse,
  Page,
} from "./types.ts";

export function getAdminBooks(
  params: {
    includeDeleted?: boolean;
    page?: number;
    size?: number;
    sort?: string;
  } = {},
): Promise<Page<AdminBookResponse>> {
  return unwrap(
    apiClient.get<ApiResponse<Page<AdminBookResponse>>>("/catalog/admin/books", { params }),
  );
}

export function getAdminBook(bookId: number | string): Promise<AdminBookResponse> {
  return unwrap(apiClient.get<ApiResponse<AdminBookResponse>>(`/catalog/admin/books/${bookId}`));
}

export function createBook(body: AdminBookCreateRequest): Promise<AdminBookResponse> {
  return unwrap(apiClient.post<ApiResponse<AdminBookResponse>>("/catalog/admin/books", body));
}

export function updateBook(
  bookId: number | string,
  body: AdminBookUpdateRequest,
): Promise<AdminBookResponse> {
  return unwrap(
    apiClient.patch<ApiResponse<AdminBookResponse>>(`/catalog/admin/books/${bookId}`, body),
  );
}

export async function deleteBook(bookId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/admin/books/${bookId}`));
}

export function rebuildRecommendationIndex(): Promise<number> {
  return unwrap(
    apiClient.post<ApiResponse<number>>("/catalog/admin/books/recommendation-index/rebuild"),
  );
}
