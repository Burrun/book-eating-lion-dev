import { useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Modal from "../../components/Modal.jsx";
import { useToast } from "../../components/Toast.jsx";
import {
  createBook,
  deleteBook,
  getAdminBooks,
  issueEpubUploadUrl,
  updateBook,
  uploadEpubFile,
} from "../../api/admin/books.ts";
import { getAdminCategories } from "../../api/admin/categories.ts";
import type { AdminBookResponse, SaleStatus } from "../../api/types.ts";

const BOOKS_QUERY_KEY = ["admin", "books"];
const PAGE_SIZE = 20;

const SALE_STATUS_LABEL: Record<SaleStatus, string> = {
  ON_SALE: "판매중",
  STOPPED: "판매중지",
  OUT_OF_STOCK: "품절",
};

interface BookFormValues {
  title: string;
  author: string;
  publisher: string;
  isbn: string;
  category: string;
  price: string;
  coverImageUrl: string;
  description: string;
  detailedSynopsis: string;
  saleStatus: SaleStatus;
  publishedDate: string;
  epubS3Key: string;
}

const EMPTY_FORM: BookFormValues = {
  title: "",
  author: "",
  publisher: "",
  isbn: "",
  category: "",
  price: "0",
  coverImageUrl: "",
  description: "",
  detailedSynopsis: "",
  saleStatus: "ON_SALE",
  publishedDate: "",
  epubS3Key: "",
};

export default function AdminBookPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const invalidate = () => queryClient.invalidateQueries({ queryKey: BOOKS_QUERY_KEY });
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const {
    data: bookPage,
    isPending,
    isError,
  } = useQuery({
    queryKey: [...BOOKS_QUERY_KEY, page],
    queryFn: () => getAdminBooks({ page, size: PAGE_SIZE }),
  });

  const { data: categories } = useQuery({
    queryKey: ["admin", "categories"],
    queryFn: getAdminCategories,
  });
  const activeCategories = (categories ?? []).filter((c) => c.active);

  const create = useMutation({
    mutationFn: createBook,
    onSuccess: () => {
      invalidate();
      toast.success("신간을 등록했습니다");
    },
    onError: () => toast.error("신간 등록에 실패했습니다 (ISBN 중복 여부를 확인해주세요)"),
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Parameters<typeof updateBook>[1] }) =>
      updateBook(id, body),
    onSuccess: () => {
      invalidate();
      toast.success("도서 정보를 수정했습니다");
    },
    onError: () => toast.error("도서 수정에 실패했습니다"),
  });

  const remove = useMutation({
    mutationFn: deleteBook,
    onSuccess: () => {
      invalidate();
      toast.success("도서를 삭제했습니다");
    },
    onError: () => toast.error("도서 삭제에 실패했습니다"),
  });

  const [editingBook, setEditingBook] = useState<AdminBookResponse | null>(null);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">📚 신간 등록</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          도서를 등록합니다. EPUB을 함께 올리면 등록 즉시 AI 검색 인제스트가 큐로 발행됩니다(로컬
          환경에서는 S3가 없어 발행이 로그로만 남습니다).
        </p>
        <BookForm
          categories={activeCategories}
          onSubmit={(values) => create.mutate(toWriteBody(values))}
          isSubmitting={create.isPending}
        />
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">전체 도서</h2>

        {isPending ? (
          <div className="flex flex-col gap-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="skeleton-shimmer h-16 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            도서 목록을 불러오지 못했습니다.
          </p>
        ) : !bookPage || bookPage.content.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            등록된 도서가 없습니다.
          </p>
        ) : (
          <>
            <ul className="flex flex-col gap-3">
              {bookPage.content.map((book) => (
                <BookItem
                  key={book.bookId}
                  book={book}
                  onEdit={() => setEditingBook(book)}
                  onDelete={() => remove.mutate(book.bookId)}
                  isBusy={remove.isPending && remove.variables === book.bookId}
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
                {bookPage.number + 1} / {bookPage.totalPages}
              </span>
              <button
                type="button"
                disabled={bookPage.last}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)] disabled:opacity-40"
              >
                다음
              </button>
            </div>
          </>
        )}
      </section>

      <Modal
        isOpen={editingBook !== null}
        onClose={() => setEditingBook(null)}
        title="도서 정보 수정"
        footer={null}
        size="lg"
      >
        {editingBook && (
          <BookForm
            categories={activeCategories}
            initialValues={{
              title: editingBook.title,
              author: editingBook.author,
              publisher: editingBook.publisher,
              isbn: editingBook.isbn,
              category: editingBook.category,
              price: String(editingBook.price),
              coverImageUrl: editingBook.coverImageUrl ?? "",
              description: editingBook.description ?? "",
              detailedSynopsis: editingBook.detailedSynopsis ?? "",
              saleStatus: editingBook.saleStatus,
              publishedDate: editingBook.publishedDate ?? "",
              epubS3Key: editingBook.epubS3Key ?? "",
            }}
            submitLabel="수정 완료"
            isSubmitting={update.isPending}
            onSubmit={(values) => {
              update.mutate(
                { id: editingBook.bookId, body: toWriteBody(values) },
                { onSuccess: () => setEditingBook(null) },
              );
            }}
          />
        )}
      </Modal>
    </main>
  );
}

function toWriteBody(values: BookFormValues) {
  return {
    title: values.title,
    author: values.author,
    publisher: values.publisher,
    isbn: values.isbn,
    category: values.category,
    price: Number(values.price),
    coverImageUrl: values.coverImageUrl.trim() || undefined,
    description: values.description.trim() || undefined,
    detailedSynopsis: values.detailedSynopsis.trim() || undefined,
    epubS3Key: values.epubS3Key.trim() || undefined,
    saleStatus: values.saleStatus,
    publishedDate: values.publishedDate || undefined,
  };
}

interface CategoryOption {
  categoryId: number;
  categoryName: string;
}

interface BookFormProps {
  categories: CategoryOption[];
  initialValues?: BookFormValues;
  submitLabel?: string;
  isSubmitting: boolean;
  onSubmit: (values: BookFormValues) => void;
}

function BookForm({
  categories,
  initialValues,
  submitLabel = "신간 등록",
  isSubmitting,
  onSubmit,
}: BookFormProps) {
  const toast = useToast() as { error: (message: string) => void };
  const [values, setValues] = useState<BookFormValues>(initialValues ?? EMPTY_FORM);
  const [epubFileName, setEpubFileName] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);

  const updateField =
    (field: keyof BookFormValues) =>
    (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
      setValues((prev) => ({ ...prev, [field]: e.target.value }));

  const handleEpubSelect = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setIsUploading(true);
    try {
      const { uploadUrl, epubS3Key } = await issueEpubUploadUrl(file.name);
      await uploadEpubFile(uploadUrl, file);
      setValues((prev) => ({ ...prev, epubS3Key }));
      setEpubFileName(file.name);
    } catch {
      toast.error("EPUB 업로드에 실패했습니다. 로컬 환경에서는 S3 저장소 모드가 아니면 지원되지 않습니다.");
    } finally {
      setIsUploading(false);
      e.target.value = "";
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!values.title.trim() || !values.author.trim() || !values.publisher.trim()) {
      toast.error("제목, 저자, 출판사를 입력해주세요.");
      return;
    }
    if (!/^\d{13}$/.test(values.isbn.trim())) {
      toast.error("ISBN은 숫자 13자리여야 합니다.");
      return;
    }
    if (!values.category) {
      toast.error("카테고리를 선택해주세요.");
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
        <FormField label="제목">
          <input
            type="text"
            value={values.title}
            onChange={updateField("title")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="저자">
          <input
            type="text"
            value={values.author}
            onChange={updateField("author")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <FormField label="출판사">
          <input
            type="text"
            value={values.publisher}
            onChange={updateField("publisher")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="ISBN (숫자 13자리)">
          <input
            type="text"
            value={values.isbn}
            onChange={updateField("isbn")}
            placeholder="9791100000001"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="카테고리">
          <select
            value={values.category}
            onChange={updateField("category")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          >
            <option value="">선택</option>
            {categories.map((c) => (
              <option key={c.categoryId} value={c.categoryName}>
                {c.categoryName}
              </option>
            ))}
          </select>
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <FormField label="가격(원)">
          <input
            type="number"
            min={0}
            value={values.price}
            onChange={updateField("price")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="판매 상태">
          <select
            value={values.saleStatus}
            onChange={updateField("saleStatus")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          >
            {Object.entries(SALE_STATUS_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="출간일">
          <input
            type="date"
            value={values.publishedDate}
            onChange={updateField("publishedDate")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
      </div>

      <FormField label="표지 이미지 URL">
        <input
          type="text"
          value={values.coverImageUrl}
          onChange={updateField("coverImageUrl")}
          placeholder="https://cdn.example.com/covers/xxx.jpg"
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
      </FormField>

      <FormField label="소개">
        <textarea
          value={values.description}
          onChange={updateField("description")}
          rows={2}
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
      </FormField>

      <FormField label="상세 줄거리">
        <textarea
          value={values.detailedSynopsis}
          onChange={updateField("detailedSynopsis")}
          rows={2}
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
      </FormField>

      <FormField label="EPUB 파일 (선택 — 안 올리면 종이책만 등록)">
        <input
          type="file"
          accept=".epub,application/epub+zip"
          disabled={isUploading}
          onChange={handleEpubSelect}
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm file:mr-3 file:rounded-md file:border-0 file:bg-[var(--color-forest)]/10 file:px-3 file:py-1.5 file:text-sm disabled:opacity-50"
        />
        {isUploading && (
          <span className="mt-1 text-xs text-[var(--color-ink)] opacity-60">업로드 중...</span>
        )}
        {!isUploading && values.epubS3Key && (
          <span className="mt-1 text-xs text-[var(--color-forest)]">
            업로드 완료: {epubFileName ?? values.epubS3Key}
          </span>
        )}
      </FormField>

      <button
        type="submit"
        disabled={isSubmitting || isUploading}
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

interface BookItemProps {
  book: AdminBookResponse;
  onEdit: () => void;
  onDelete: () => void;
  isBusy: boolean;
}

function BookItem({ book, onEdit, onDelete, isBusy }: BookItemProps) {
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-semibold text-[var(--color-ink)]">{book.title}</p>
          {book.ebookAvailable && (
            <span className="rounded-full bg-[var(--color-honey)]/20 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
              eBook
            </span>
          )}
          {book.deleted && (
            <span className="rounded-full bg-[var(--color-coral)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-coral)]">
              삭제됨
            </span>
          )}
        </div>
        <p className="text-sm text-[var(--color-ink)] opacity-60">
          {book.author} · {book.category} · {book.price.toLocaleString()}원 ·{" "}
          {SALE_STATUS_LABEL[book.saleStatus]}
        </p>
      </div>
      <div className="flex shrink-0 gap-2">
        <button
          type="button"
          onClick={onEdit}
          className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)]"
        >
          수정
        </button>
        {!book.deleted && (
          <button
            type="button"
            disabled={isBusy}
            onClick={onDelete}
            className="rounded-lg border border-[var(--color-coral)]/30 px-3 py-1.5 text-xs font-semibold text-[var(--color-coral)] transition hover:bg-[var(--color-coral)]/10 disabled:opacity-40"
          >
            삭제
          </button>
        )}
      </div>
    </li>
  );
}
