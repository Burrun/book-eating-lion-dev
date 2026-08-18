import { useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Modal from "../../components/Modal.jsx";
import { useToast } from "../../components/Toast.jsx";
import { createFaq, deactivateFaq, getAdminFaqs, updateFaq } from "../../api/admin/faqs.ts";
import type { FaqResponse } from "../../api/types.ts";

const FAQS_QUERY_KEY = (category: string) => ["admin", "faqs", category];

interface FaqFormValues {
  category: string;
  question: string;
  answer: string;
  sortOrder: string;
  active: boolean;
}

const EMPTY_FORM: FaqFormValues = {
  category: "",
  question: "",
  answer: "",
  sortOrder: "0",
  active: true,
};

export default function AdminFaqPage() {
  const queryClient = useQueryClient();
  const [categoryFilter, setCategoryFilter] = useState("");
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["admin", "faqs"] });
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const {
    data: faqs,
    isPending,
    isError,
  } = useQuery({
    queryKey: FAQS_QUERY_KEY(categoryFilter),
    queryFn: () => getAdminFaqs(categoryFilter || undefined),
  });

  const create = useMutation({
    mutationFn: createFaq,
    onSuccess: () => {
      invalidate();
      toast.success("FAQ를 등록했습니다");
    },
    onError: () => toast.error("FAQ 등록에 실패했습니다"),
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Parameters<typeof updateFaq>[1] }) =>
      updateFaq(id, body),
    onSuccess: () => {
      invalidate();
      toast.success("FAQ를 수정했습니다");
    },
    onError: () => toast.error("FAQ 수정에 실패했습니다"),
  });

  const deactivate = useMutation({
    mutationFn: deactivateFaq,
    onSuccess: () => {
      invalidate();
      toast.success("FAQ를 비활성화했습니다");
    },
    onError: () => toast.error("FAQ 비활성화에 실패했습니다"),
  });

  const [editingFaq, setEditingFaq] = useState<FaqResponse | null>(null);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">❓ FAQ 관리</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          자주 묻는 질문을 등록/수정/비활성화합니다.
        </p>
        <FaqForm
          onSubmit={(values) => create.mutate(toWriteBody(values))}
          isSubmitting={create.isPending}
        />
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-display text-lg text-[var(--color-forest)]">전체 FAQ</h2>
          <input
            type="text"
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            placeholder="카테고리로 필터링 (예: 배송)"
            className="rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </div>

        {isPending ? (
          <div className="grid gap-3">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer h-20 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            FAQ 목록을 불러오지 못했습니다.
          </p>
        ) : !faqs || faqs.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            등록된 FAQ가 없습니다.
          </p>
        ) : (
          <ul className="flex flex-col gap-3">
            {faqs.map((faq) => (
              <FaqItem
                key={faq.faqId}
                faq={faq}
                onEdit={() => setEditingFaq(faq)}
                onDeactivate={() => deactivate.mutate(faq.faqId)}
                isBusy={deactivate.isPending && deactivate.variables === faq.faqId}
              />
            ))}
          </ul>
        )}
      </section>

      <Modal
        isOpen={editingFaq !== null}
        onClose={() => setEditingFaq(null)}
        title="FAQ 수정"
        footer={null}
      >
        {editingFaq && (
          <FaqForm
            initialValues={{
              category: editingFaq.category,
              question: editingFaq.question,
              answer: editingFaq.answer,
              sortOrder: String(editingFaq.sortOrder),
              active: editingFaq.active,
            }}
            submitLabel="수정 완료"
            isSubmitting={update.isPending}
            onSubmit={(values) => {
              update.mutate(
                { id: editingFaq.faqId, body: toWriteBody(values) },
                { onSuccess: () => setEditingFaq(null) },
              );
            }}
          />
        )}
      </Modal>
    </main>
  );
}

function toWriteBody(values: FaqFormValues) {
  return {
    category: values.category,
    question: values.question,
    answer: values.answer,
    sortOrder: Number(values.sortOrder),
    active: values.active,
  };
}

interface FaqFormProps {
  initialValues?: FaqFormValues;
  submitLabel?: string;
  isSubmitting: boolean;
  onSubmit: (values: FaqFormValues) => void;
}

function FaqForm({
  initialValues,
  submitLabel = "FAQ 등록",
  isSubmitting,
  onSubmit,
}: FaqFormProps) {
  const toast = useToast() as { error: (message: string) => void };
  const [values, setValues] = useState<FaqFormValues>(initialValues ?? EMPTY_FORM);

  const updateField =
    (field: "category" | "question" | "answer" | "sortOrder") =>
    (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      setValues((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!values.category.trim() || !values.question.trim() || !values.answer.trim()) {
      toast.error("카테고리, 질문, 답변을 모두 입력해주세요.");
      return;
    }
    onSubmit(values);
  };

  return (
    <form
      className="flex flex-col gap-3 rounded-xl border border-[var(--color-forest)]/10 bg-[var(--color-paper)] p-4"
      onSubmit={handleSubmit}
    >
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FormField label="카테고리">
          <input
            type="text"
            value={values.category}
            onChange={updateField("category")}
            placeholder="예: 배송"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="정렬 순서">
          <input
            type="number"
            min={0}
            value={values.sortOrder}
            onChange={updateField("sortOrder")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
      </div>

      <FormField label="질문">
        <input
          type="text"
          value={values.question}
          onChange={updateField("question")}
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
      </FormField>

      <FormField label="답변">
        <textarea
          value={values.answer}
          onChange={updateField("answer")}
          rows={3}
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
      </FormField>

      <label className="flex items-center gap-2 text-sm text-[var(--color-ink)] opacity-80">
        <input
          type="checkbox"
          checked={values.active}
          onChange={(e) => setValues((prev) => ({ ...prev, active: e.target.checked }))}
        />
        공개(활성)
      </label>

      <button
        type="submit"
        disabled={isSubmitting}
        className="self-end rounded-lg bg-[var(--color-honey)] px-6 py-2.5 text-sm font-semibold text-[var(--color-forest)] shadow-sm transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isSubmitting ? "저장 중..." : submitLabel}
      </button>
    </form>
  );
}

function FormField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">{label}</span>
      {children}
    </label>
  );
}

interface FaqItemProps {
  faq: FaqResponse;
  onEdit: () => void;
  onDeactivate: () => void;
  isBusy: boolean;
}

function FaqItem({ faq, onEdit, onDeactivate, isBusy }: FaqItemProps) {
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4">
      <div className="flex items-center gap-2">
        <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
          {faq.category}
        </span>
        {!faq.active && (
          <span className="rounded-full bg-[var(--color-coral)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-coral)]">
            비활성
          </span>
        )}
      </div>
      <p className="text-sm font-semibold text-[var(--color-ink)]">{faq.question}</p>
      <p className="text-sm text-[var(--color-ink)] opacity-70">{faq.answer}</p>

      <div className="mt-1 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={onEdit}
          className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)]"
        >
          수정
        </button>
        {faq.active && (
          <button
            type="button"
            disabled={isBusy}
            onClick={onDeactivate}
            className="rounded-lg border border-[var(--color-coral)]/30 px-3 py-1.5 text-xs font-semibold text-[var(--color-coral)] transition hover:bg-[var(--color-coral)]/10 disabled:opacity-40"
          >
            비활성화
          </button>
        )}
      </div>
    </li>
  );
}
