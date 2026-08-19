import { apiClient, unwrap } from "./client.ts";
import type { ApiResponse, FaqRequest, FaqResponse } from "./types.ts";

export function getFaqs(category?: string): Promise<FaqResponse[]> {
  return unwrap(
    apiClient.get<ApiResponse<FaqResponse[]>>("/catalog/faqs", {
      params: category ? { category } : undefined,
    }),
  );
}

export function getAdminFaqs(category?: string): Promise<FaqResponse[]> {
  return unwrap(
    apiClient.get<ApiResponse<FaqResponse[]>>("/catalog/admin/faqs", {
      params: category ? { category } : undefined,
    }),
  );
}

export function createFaq(body: FaqRequest): Promise<FaqResponse> {
  return unwrap(apiClient.post<ApiResponse<FaqResponse>>("/catalog/admin/faqs", body));
}

export function updateFaq(faqId: number | string, body: FaqRequest): Promise<FaqResponse> {
  return unwrap(apiClient.patch<ApiResponse<FaqResponse>>(`/catalog/admin/faqs/${faqId}`, body));
}

export async function deleteFaq(faqId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/admin/faqs/${faqId}`));
}
