import type { FaqResponse, FaqWriteRequest } from "../../api/types.ts";

let faqs: FaqResponse[] = [
  {
    faqId: 1,
    category: "배송",
    question: "배송은 얼마나 걸리나요?",
    answer: "결제 완료 후 1~2일 내 출고됩니다.",
    sortOrder: 0,
    active: true,
    createdAt: "2026-07-01T09:00:00",
    updatedAt: "2026-07-01T09:00:00",
  },
  {
    faqId: 2,
    category: "결제",
    question: "카카오페이로 결제할 수 있나요?",
    answer: "네, 카카오페이 결제를 지원합니다.",
    sortOrder: 0,
    active: true,
    createdAt: "2026-07-02T09:00:00",
    updatedAt: "2026-07-02T09:00:00",
  },
];

function nextFaqId(): number {
  return faqs.reduce((max, f) => Math.max(max, f.faqId), 0) + 1;
}

export function mockGetAdminFaqs(category?: string): FaqResponse[] {
  return category ? faqs.filter((f) => f.category === category) : faqs;
}

export function mockCreateFaq(body: FaqWriteRequest): FaqResponse {
  const now = new Date().toISOString();
  const faq: FaqResponse = { faqId: nextFaqId(), ...body, createdAt: now, updatedAt: now };
  faqs = [...faqs, faq];
  return faq;
}

export function mockUpdateFaq(
  faqId: number | string,
  body: FaqWriteRequest,
): FaqResponse | undefined {
  let updated: FaqResponse | undefined;
  faqs = faqs.map((f) => {
    if (String(f.faqId) !== String(faqId)) return f;
    updated = { ...f, ...body, updatedAt: new Date().toISOString() };
    return updated;
  });
  return updated;
}

export function mockDeactivateFaq(faqId: number | string): void {
  faqs = faqs.map((f) => (String(f.faqId) === String(faqId) ? { ...f, active: false } : f));
}
