import { apiClient, unwrap } from "./client.ts";
import type { ApiResponse, CategoryRequest, CategoryResponse } from "./types.ts";

export function getCategories(): Promise<CategoryResponse[]> {
  return unwrap(apiClient.get<ApiResponse<CategoryResponse[]>>("/catalog/categories"));
}

export function getAdminCategories(): Promise<CategoryResponse[]> {
  return unwrap(apiClient.get<ApiResponse<CategoryResponse[]>>("/catalog/admin/categories"));
}

export function getAdminCategory(categoryId: number | string): Promise<CategoryResponse> {
  return unwrap(
    apiClient.get<ApiResponse<CategoryResponse>>(`/catalog/admin/categories/${categoryId}`),
  );
}

export function createCategory(body: CategoryRequest): Promise<CategoryResponse> {
  return unwrap(apiClient.post<ApiResponse<CategoryResponse>>("/catalog/admin/categories", body));
}

export function updateCategory(
  categoryId: number | string,
  body: CategoryRequest,
): Promise<CategoryResponse> {
  return unwrap(
    apiClient.patch<ApiResponse<CategoryResponse>>(`/catalog/admin/categories/${categoryId}`, body),
  );
}

export async function deleteCategory(categoryId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/admin/categories/${categoryId}`));
}
