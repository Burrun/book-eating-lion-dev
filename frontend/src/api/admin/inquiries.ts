import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import { mockAnswerInquiry, mockGetAdminInquiries } from "../../mocks/admin/inquiries.ts";
import type { ApiResponse, InquiryResponse, InquiryStatus, Page } from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/admin/inquiries — 관리자용 상품문의 목록 (비공개 문의 포함)
export async function getAdminInquiries(
  params: {
    bookId?: number | string;
    status?: InquiryStatus;
    page?: number;
    size?: number;
  } = {},
): Promise<Page<InquiryResponse>> {
  if (USE_MOCK) return mockDelay(mockGetAdminInquiries(params));
  return unwrap(
    apiClient.get<ApiResponse<Page<InquiryResponse>>>("/catalog/admin/inquiries", { params }),
  );
}

// PATCH /api/catalog/admin/inquiries/{inquiryId}/answer — 문의 답변 등록/수정
export async function answerInquiry(
  inquiryId: number | string,
  answer: string,
): Promise<InquiryResponse> {
  if (USE_MOCK) {
    const updated = mockAnswerInquiry(inquiryId, answer);
    if (!updated) throw new Error("문의를 찾을 수 없습니다.");
    return mockDelay(updated);
  }
  return unwrap(
    apiClient.patch<ApiResponse<InquiryResponse>>(`/catalog/admin/inquiries/${inquiryId}/answer`, {
      answer,
    }),
  );
}
