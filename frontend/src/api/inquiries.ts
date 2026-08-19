import { apiClient, unwrap } from "./client.ts";
import type {
  ApiResponse,
  InquiryAnswerRequest,
  InquiryRequest,
  InquiryResponse,
  InquiryStatus,
  Page,
} from "./types.ts";

export function getBookInquiries(
  bookId: number | string,
  params: { page?: number; size?: number; sort?: string } = {},
): Promise<Page<InquiryResponse>> {
  return unwrap(
    apiClient.get<ApiResponse<Page<InquiryResponse>>>(`/catalog/books/${bookId}/inquiries`, {
      params,
    }),
  );
}

export function createInquiry(
  bookId: number | string,
  body: InquiryRequest,
): Promise<InquiryResponse> {
  return unwrap(
    apiClient.post<ApiResponse<InquiryResponse>>(`/catalog/books/${bookId}/inquiries`, body),
  );
}

export function updateInquiry(
  inquiryId: number | string,
  body: InquiryRequest,
): Promise<InquiryResponse> {
  return unwrap(
    apiClient.patch<ApiResponse<InquiryResponse>>(`/catalog/inquiries/${inquiryId}`, body),
  );
}

export async function deleteInquiry(inquiryId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/inquiries/${inquiryId}`));
}

export function getAdminInquiries(
  params: {
    bookId?: number;
    status?: InquiryStatus;
    page?: number;
    size?: number;
    sort?: string;
  } = {},
): Promise<Page<InquiryResponse>> {
  return unwrap(
    apiClient.get<ApiResponse<Page<InquiryResponse>>>("/catalog/admin/inquiries", { params }),
  );
}

export function answerInquiry(
  inquiryId: number | string,
  body: InquiryAnswerRequest,
): Promise<InquiryResponse> {
  return unwrap(
    apiClient.patch<ApiResponse<InquiryResponse>>(
      `/catalog/admin/inquiries/${inquiryId}/answer`,
      body,
    ),
  );
}
