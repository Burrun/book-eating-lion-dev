import { useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Modal from "../../components/Modal.jsx";
import { useToast } from "../../components/Toast.jsx";
import {
  createCategory,
  deactivateCategory,
  getAdminCategories,
  updateCategory,
} from "../../api/admin/categories.ts";
import type { CategoryResponse } from "../../api/types.ts";

const CATEGORIES_QUERY_KEY = ["admin", "categories"];

interface CategoryFormValues {
  categoryName: string;
  parentId: string;
  sortOrder: string;
}

const EMPTY_FORM: CategoryFormValues = { categoryName: "", parentId: "", sortOrder: "0" };

export default function AdminCategoriesPage() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: CATEGORIES_QUERY_KEY });
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const {
    data: categories,
    isPending,
    isError,
  } = useQuery({ queryKey: CATEGORIES_QUERY_KEY, queryFn: getAdminCategories });

  const create = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      invalidate();
      toast.success("카테고리를 등록했습니다");
    },
    onError: () => toast.error("카테고리 등록에 실패했습니다"),
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Parameters<typeof updateCategory>[1] }) =>
      updateCategory(id, body),
    onSuccess: () => {
      invalidate();
      toast.success("카테고리를 수정했습니다");
    },
    onError: () => toast.error("카테고리 수정에 실패했습니다"),
  });

  const deactivate = useMutation({
    mutationFn: deactivateCategory,
    onSuccess: () => {
      invalidate();
      toast.success("카테고리를 비활성화했습니다");
    },
    onError: () => toast.error("활성 도서가 연결되어 있어 비활성화할 수 없습니다"),
  });

  const [editingCategory, setEditingCategory] = useState<CategoryResponse | null>(null);

  const parentOptions = (categories ?? []).filter((c) => c.active);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">🗂️ 카테고리 관리</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          도서 분류에 쓰이는 카테고리를 등록/수정/비활성화합니다.
        </p>
        <CategoryForm
          parentOptions={parentOptions}
          onSubmit={(values) =>
            create.mutate({
              categoryName: values.categoryName,
              parentId: values.parentId ? Number(values.parentId) : null,
              sortOrder: Number(values.sortOrder),
            })
          }
          isSubmitting={create.isPending}
        />
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">전체 카테고리</h2>

        {isPending ? (
          <div className="grid gap-3 sm:grid-cols-2">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer h-20 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            카테고리 목록을 불러오지 못했습니다.
          </p>
        ) : !categories || categories.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            등록된 카테고리가 없습니다.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {categories.map((category) => (
              <CategoryItem
                key={category.categoryId}
                category={category}
                parentName={
                  categories.find((c) => c.categoryId === category.parentId)?.categoryName
                }
                onEdit={() => setEditingCategory(category)}
                onDeactivate={() => deactivate.mutate(category.categoryId)}
                isBusy={deactivate.isPending && deactivate.variables === category.categoryId}
              />
            ))}
          </ul>
        )}
      </section>

      <Modal
        isOpen={editingCategory !== null}
        onClose={() => setEditingCategory(null)}
        title="카테고리 수정"
        footer={null}
      >
        {editingCategory && (
          <CategoryForm
            parentOptions={parentOptions.filter((c) => c.categoryId !== editingCategory.categoryId)}
            initialValues={{
              categoryName: editingCategory.categoryName,
              parentId: editingCategory.parentId != null ? String(editingCategory.parentId) : "",
              sortOrder: String(editingCategory.sortOrder),
            }}
            submitLabel="수정 완료"
            isSubmitting={update.isPending}
            onSubmit={(values) => {
              update.mutate(
                {
                  id: editingCategory.categoryId,
                  body: {
                    categoryName: values.categoryName,
                    parentId: values.parentId ? Number(values.parentId) : null,
                    sortOrder: Number(values.sortOrder),
                  },
                },
                { onSuccess: () => setEditingCategory(null) },
              );
            }}
          />
        )}
      </Modal>
    </main>
  );
}

interface CategoryFormProps {
  parentOptions: CategoryResponse[];
  initialValues?: CategoryFormValues;
  submitLabel?: string;
  isSubmitting: boolean;
  onSubmit: (values: CategoryFormValues) => void;
}

function CategoryForm({
  parentOptions,
  initialValues,
  submitLabel = "카테고리 등록",
  isSubmitting,
  onSubmit,
}: CategoryFormProps) {
  const toast = useToast() as { error: (message: string) => void };
  const [values, setValues] = useState<CategoryFormValues>(initialValues ?? EMPTY_FORM);

  const updateField = (field: keyof CategoryFormValues) => (e: ChangeEvent<HTMLInputElement>) =>
    setValues((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!values.categoryName.trim()) {
      toast.error("카테고리명을 입력해주세요.");
      return;
    }
    onSubmit(values);
  };

  return (
    <form
      className="flex flex-col gap-3 rounded-xl border border-[var(--color-forest)]/10 bg-[var(--color-paper)] p-4"
      onSubmit={handleSubmit}
    >
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <FormField label="카테고리명">
          <input
            type="text"
            value={values.categoryName}
            onChange={updateField("categoryName")}
            placeholder="예: IT/컴퓨터"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="상위 카테고리">
          <select
            value={values.parentId}
            onChange={(e) => setValues((prev) => ({ ...prev, parentId: e.target.value }))}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          >
            <option value="">없음</option>
            {parentOptions.map((c) => (
              <option key={c.categoryId} value={c.categoryId}>
                {c.categoryName}
              </option>
            ))}
          </select>
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

interface CategoryItemProps {
  category: CategoryResponse;
  parentName?: string;
  onEdit: () => void;
  onDeactivate: () => void;
  isBusy: boolean;
}

function CategoryItem({ category, parentName, onEdit, onDeactivate, isBusy }: CategoryItemProps) {
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4">
      <div className="flex items-center gap-2">
        <span className="text-sm font-semibold text-[var(--color-ink)]">
          {category.categoryName}
        </span>
        {!category.active && (
          <span className="rounded-full bg-[var(--color-coral)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-coral)]">
            비활성
          </span>
        )}
      </div>
      <p className="text-sm text-[var(--color-ink)] opacity-60">
        상위: {parentName ?? "없음"} · 정렬 {category.sortOrder}
      </p>

      <div className="mt-1 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={onEdit}
          className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)]"
        >
          수정
        </button>
        {category.active && (
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
