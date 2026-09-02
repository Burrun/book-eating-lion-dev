import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import {
  mockCreateCategory,
  mockDeactivateCategory,
  mockGetAdminCategories,
  mockUpdateCategory,
} from "../../mocks/admin/categories.ts";
import type {
  ApiResponse,
  CategoryCreateRequest,
  CategoryResponse,
  CategoryUpdateRequest,
} from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/admin/categories — 관리자용 전체 카테고리 목록 (비활성 포함)
export async function getAdminCategories(): Promise<CategoryResponse[]> {
  if (USE_MOCK) return mockDelay(mockGetAdminCategories());
  return unwrap(apiClient.get<ApiResponse<CategoryResponse[]>>("/catalog/admin/categories"));
}

// POST /api/catalog/admin/categories — 카테고리 등록
export async function createCategory(body: CategoryCreateRequest): Promise<CategoryResponse> {
  if (USE_MOCK) return mockDelay(mockCreateCategory(body));
  return unwrap(apiClient.post<ApiResponse<CategoryResponse>>("/catalog/admin/categories", body));
}

// PATCH /api/catalog/admin/categories/{categoryId} — 카테고리 수정
export async function updateCategory(
  categoryId: number | string,
  body: CategoryUpdateRequest,
): Promise<CategoryResponse> {
  if (USE_MOCK) {
    const updated = mockUpdateCategory(categoryId, body);
    if (!updated) throw new Error("카테고리를 찾을 수 없습니다.");
    return mockDelay(updated);
  }
  return unwrap(
    apiClient.patch<ApiResponse<CategoryResponse>>(`/catalog/admin/categories/${categoryId}`, body),
  );
}

// DELETE /api/catalog/admin/categories/{categoryId} — 카테고리 비활성화 (하드 삭제 아님)
export async function deactivateCategory(categoryId: number | string): Promise<void> {
  if (USE_MOCK) {
    mockDeactivateCategory(categoryId);
    await mockDelay(undefined);
    return;
  }
  await apiClient.delete(`/catalog/admin/categories/${categoryId}`);
}
