import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import {
  mockCreateFaq,
  mockDeactivateFaq,
  mockGetAdminFaqs,
  mockUpdateFaq,
} from "../../mocks/admin/faqs.ts";
import type { ApiResponse, FaqResponse, FaqWriteRequest } from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/admin/faqs — 관리자용 FAQ 목록 (비활성 포함, category로 필터 가능)
export async function getAdminFaqs(category?: string): Promise<FaqResponse[]> {
  if (USE_MOCK) return mockDelay(mockGetAdminFaqs(category));
  return unwrap(
    apiClient.get<ApiResponse<FaqResponse[]>>("/catalog/admin/faqs", { params: { category } }),
  );
}

// POST /api/catalog/admin/faqs — FAQ 등록
export async function createFaq(body: FaqWriteRequest): Promise<FaqResponse> {
  if (USE_MOCK) return mockDelay(mockCreateFaq(body));
  return unwrap(apiClient.post<ApiResponse<FaqResponse>>("/catalog/admin/faqs", body));
}

// PATCH /api/catalog/admin/faqs/{faqId} — FAQ 수정
export async function updateFaq(
  faqId: number | string,
  body: FaqWriteRequest,
): Promise<FaqResponse> {
  if (USE_MOCK) {
    const updated = mockUpdateFaq(faqId, body);
    if (!updated) throw new Error("FAQ를 찾을 수 없습니다.");
    return mockDelay(updated);
  }
  return unwrap(apiClient.patch<ApiResponse<FaqResponse>>(`/catalog/admin/faqs/${faqId}`, body));
}

// DELETE /api/catalog/admin/faqs/{faqId} — FAQ 비활성화 (하드 삭제 아님)
export async function deactivateFaq(faqId: number | string): Promise<void> {
  if (USE_MOCK) {
    mockDeactivateFaq(faqId);
    await mockDelay(undefined);
    return;
  }
  await apiClient.delete(`/catalog/admin/faqs/${faqId}`);
}
