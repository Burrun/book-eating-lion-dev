import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../components/Toast.jsx";
import { answerInquiry, getAdminInquiries } from "../../api/admin/inquiries.ts";
import type { InquiryResponse, InquiryStatus } from "../../api/types.ts";

const PAGE_SIZE = 20;

export default function AdminInquiriesPage() {
  const queryClient = useQueryClient();
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const [bookIdFilter, setBookIdFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<InquiryStatus | "">("");
  const [page, setPage] = useState(0);

  const queryKey = ["admin", "inquiries", bookIdFilter, statusFilter, page];

  const {
    data: inquiryPage,
    isPending,
    isError,
  } = useQuery({
    queryKey,
    queryFn: () =>
      getAdminInquiries({
        bookId: bookIdFilter || undefined,
        status: statusFilter || undefined,
        page,
        size: PAGE_SIZE,
      }),
  });

  const answer = useMutation({
    mutationFn: ({ id, answer }: { id: number; answer: string }) => answerInquiry(id, answer),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "inquiries"] });
      toast.success("답변을 등록했습니다");
    },
    onError: () => toast.error("답변 등록에 실패했습니다"),
  });

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">✉️ 상품문의 관리</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          회원이 남긴 상품문의를 조회하고 답변합니다.
        </p>

        <div className="flex flex-wrap gap-3">
          <input
            type="text"
            value={bookIdFilter}
            onChange={(e) => {
              setBookIdFilter(e.target.value);
              setPage(0);
            }}
            placeholder="도서 ID로 필터링"
            className="rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value as InquiryStatus | "");
              setPage(0);
            }}
            className="rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          >
            <option value="">전체 상태</option>
            <option value="WAITING">답변 대기</option>
            <option value="ANSWERED">답변 완료</option>
          </select>
        </div>
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        {isPending ? (
          <div className="flex flex-col gap-3">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer h-24 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            문의 목록을 불러오지 못했습니다.
          </p>
        ) : !inquiryPage || inquiryPage.content.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            조건에 맞는 문의가 없습니다.
          </p>
        ) : (
          <>
            <ul className="flex flex-col gap-3">
              {inquiryPage.content.map((inquiry) => (
                <InquiryItem
                  key={inquiry.inquiryId}
                  inquiry={inquiry}
                  onAnswer={(answerText) =>
                    answer.mutate({ id: inquiry.inquiryId, answer: answerText })
                  }
                  isBusy={answer.isPending && answer.variables?.id === inquiry.inquiryId}
                />
              ))}
            </ul>

            <div className="mt-4 flex items-center justify-center gap-3">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)] disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-sm text-[var(--color-ink)] opacity-70">
                {inquiryPage.number + 1} / {inquiryPage.totalPages}
              </span>
              <button
                type="button"
                disabled={inquiryPage.last}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)] disabled:opacity-40"
              >
                다음
              </button>
            </div>
          </>
        )}
      </section>
    </main>
  );
}

interface InquiryItemProps {
  inquiry: InquiryResponse;
  onAnswer: (answer: string) => void;
  isBusy: boolean;
}

function InquiryItem({ inquiry, onAnswer, isBusy }: InquiryItemProps) {
  const [draft, setDraft] = useState(inquiry.answer ?? "");

  return (
    <li className="flex flex-col gap-2 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4">
      <div className="flex items-center gap-2">
        <span className="text-sm font-semibold text-[var(--color-ink)]">{inquiry.title}</span>
        {inquiry.privateInquiry && (
          <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
            비공개
          </span>
        )}
        <span
          className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${
            inquiry.status === "ANSWERED"
              ? "bg-[var(--color-forest)]/10 text-[var(--color-forest)]"
              : "bg-[var(--color-coral)]/10 text-[var(--color-coral)]"
          }`}
        >
          {inquiry.status === "ANSWERED" ? "답변 완료" : "답변 대기"}
        </span>
      </div>
      <p className="text-sm text-[var(--color-ink)] opacity-70">
        도서 #{inquiry.bookId} · {inquiry.content}
      </p>

      <textarea
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        placeholder="답변을 입력하세요"
        rows={2}
        className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
      />
      <button
        type="button"
        disabled={isBusy || !draft.trim()}
        onClick={() => onAnswer(draft)}
        className="self-end rounded-lg bg-[var(--color-honey)] px-5 py-2 text-sm font-semibold text-[var(--color-forest)] shadow-sm transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isBusy ? "저장 중..." : inquiry.status === "ANSWERED" ? "답변 수정" : "답변 등록"}
      </button>
    </li>
  );
}
