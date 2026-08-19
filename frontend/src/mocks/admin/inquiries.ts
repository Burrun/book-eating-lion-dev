import type { InquiryResponse, InquiryStatus } from "../../api/types.ts";
import type { Page } from "../../api/types.ts";

let inquiries: InquiryResponse[] = [
  {
    inquiryId: 1,
    bookId: 1,
    memberId: "9f8c1a2b-3d4e-5f60-7a8b-9c0d1e2f3a4b",
    title: "재입고 문의",
    content: "이 책 언제 재입고되나요?",
    privateInquiry: false,
    status: "WAITING",
    answer: null,
    answeredBy: null,
    answeredAt: null,
    deleted: false,
    createdAt: "2026-08-01T10:00:00",
    updatedAt: "2026-08-01T10:00:00",
  },
  {
    inquiryId: 2,
    bookId: 2,
    memberId: "9f8c1a2b-3d4e-5f60-7a8b-9c0d1e2f3a4b",
    title: "배송 관련 문의",
    content: "분철된 상태로 오나요?",
    privateInquiry: true,
    status: "ANSWERED",
    answer: "네, 요청하신 분철 옵션으로 발송됩니다.",
    answeredBy: "admin-sub-0001",
    answeredAt: "2026-08-02T11:00:00",
    deleted: false,
    createdAt: "2026-07-30T09:00:00",
    updatedAt: "2026-08-02T11:00:00",
  },
];

export function mockGetAdminInquiries(params: {
  bookId?: number | string;
  status?: InquiryStatus;
  page?: number;
  size?: number;
}): Page<InquiryResponse> {
  const { bookId, status, page = 0, size = 20 } = params;
  const matched = inquiries.filter(
    (inq) =>
      (bookId == null || String(inq.bookId) === String(bookId)) &&
      (status == null || inq.status === status),
  );
  const totalPages = Math.max(1, Math.ceil(matched.length / size));
  const number = Math.min(Math.max(page, 0), totalPages - 1);
  return {
    content: matched.slice(number * size, number * size + size),
    number,
    size,
    totalElements: matched.length,
    totalPages,
    first: number === 0,
    last: number === totalPages - 1,
  };
}

export function mockAnswerInquiry(
  inquiryId: number | string,
  answer: string,
): InquiryResponse | undefined {
  let updated: InquiryResponse | undefined;
  inquiries = inquiries.map((inq) => {
    if (String(inq.inquiryId) !== String(inquiryId)) return inq;
    updated = {
      ...inq,
      answer,
      status: "ANSWERED",
      answeredBy: "admin-mock",
      answeredAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    return updated;
  });
  return updated;
}
