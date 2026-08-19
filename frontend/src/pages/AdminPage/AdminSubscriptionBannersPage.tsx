import { useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Modal from "../../components/Modal.jsx";
import { useToast } from "../../components/Toast.jsx";
import {
  createBanner,
  deactivateBanner,
  getAdminBanners,
  updateBanner,
} from "../../api/admin/subscriptionBanners.ts";
import type { SubscriptionBannerResponse } from "../../api/types.ts";

const BANNERS_QUERY_KEY = ["admin", "subscription-banners"];

interface BannerFormValues {
  imageUrl: string;
  title: string;
  linkUrl: string;
  startAt: string;
  endAt: string;
  sortOrder: string;
  active: boolean;
}

const EMPTY_FORM: BannerFormValues = {
  imageUrl: "",
  title: "",
  linkUrl: "",
  startAt: "",
  endAt: "",
  sortOrder: "0",
  active: true,
};

// "2026-12-31T23:59:00" -> "2026-12-31T23:59" (datetime-local 입력이 받는 형태)
function toDatetimeLocalValue(isoLike: string): string {
  return isoLike.slice(0, 16);
}

// "2026-12-31T23:59" (datetime-local 값) -> "2026-12-31T23:59:00" (백엔드 LocalDateTime 형태)
function toLocalDateTime(datetimeLocalValue: string): string {
  return datetimeLocalValue.length === 16 ? `${datetimeLocalValue}:00` : datetimeLocalValue;
}

function isExpired(banner: SubscriptionBannerResponse): boolean {
  return new Date(banner.endAt).getTime() <= Date.now();
}

export default function AdminSubscriptionBannersPage() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: BANNERS_QUERY_KEY });
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const {
    data: banners,
    isPending,
    isError,
  } = useQuery({ queryKey: BANNERS_QUERY_KEY, queryFn: getAdminBanners });

  const create = useMutation({
    mutationFn: createBanner,
    onSuccess: () => {
      invalidate();
      toast.success("배너를 등록했습니다");
    },
    onError: () => toast.error("배너 등록에 실패했습니다"),
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Parameters<typeof updateBanner>[1] }) =>
      updateBanner(id, body),
    onSuccess: () => {
      invalidate();
      toast.success("배너를 수정했습니다");
    },
    onError: () => toast.error("배너 수정에 실패했습니다"),
  });

  const deactivate = useMutation({
    mutationFn: deactivateBanner,
    onSuccess: () => {
      invalidate();
      toast.success("배너를 비활성화했습니다");
    },
    onError: () => toast.error("배너 비활성화에 실패했습니다"),
  });

  const [editingBanner, setEditingBanner] = useState<SubscriptionBannerResponse | null>(null);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">📣 정기구독 배너 관리</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          홈 화면에 노출할 정기구독 홍보 배너를 등록/수정/비활성화합니다. 실제 홈 화면 노출은 별도
          작업입니다 — 여기서는 콘텐츠만 관리합니다. 삭제 API는 없습니다 — 활성 스위치를 끄거나
          기간을 지난 시각으로 수정하면 노출에서 빠집니다.
        </p>
        <BannerForm
          onSubmit={(values) => create.mutate(toWriteBody(values))}
          isSubmitting={create.isPending}
        />
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">전체 배너</h2>

        {isPending ? (
          <div className="grid gap-3 sm:grid-cols-2">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer h-32 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            배너 목록을 불러오지 못했습니다.
          </p>
        ) : !banners || banners.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            등록된 배너가 없습니다.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {banners.map((banner) => (
              <BannerItem
                key={banner.bannerId}
                banner={banner}
                onEdit={() => setEditingBanner(banner)}
                onDeactivate={() => deactivate.mutate(banner.bannerId)}
                isBusy={deactivate.isPending && deactivate.variables === banner.bannerId}
              />
            ))}
          </ul>
        )}
      </section>

      <Modal
        isOpen={editingBanner !== null}
        onClose={() => setEditingBanner(null)}
        title="배너 수정"
        footer={null}
      >
        {editingBanner && (
          <BannerForm
            initialValues={{
              imageUrl: editingBanner.imageUrl,
              title: editingBanner.title,
              linkUrl: editingBanner.linkUrl ?? "",
              startAt: toDatetimeLocalValue(editingBanner.startAt),
              endAt: toDatetimeLocalValue(editingBanner.endAt),
              sortOrder: String(editingBanner.sortOrder),
              active: editingBanner.active,
            }}
            submitLabel="수정 완료"
            isSubmitting={update.isPending}
            onSubmit={(values) => {
              update.mutate(
                { id: editingBanner.bannerId, body: toWriteBody(values) },
                { onSuccess: () => setEditingBanner(null) },
              );
            }}
          />
        )}
      </Modal>
    </main>
  );
}

function toWriteBody(values: BannerFormValues) {
  return {
    imageUrl: values.imageUrl,
    title: values.title,
    linkUrl: values.linkUrl.trim() || null,
    startAt: toLocalDateTime(values.startAt),
    endAt: toLocalDateTime(values.endAt),
    sortOrder: Number(values.sortOrder),
    active: values.active,
  };
}

interface BannerFormProps {
  initialValues?: BannerFormValues;
  submitLabel?: string;
  isSubmitting: boolean;
  onSubmit: (values: BannerFormValues) => void;
}

function BannerForm({
  initialValues,
  submitLabel = "배너 등록",
  isSubmitting,
  onSubmit,
}: BannerFormProps) {
  const toast = useToast() as { error: (message: string) => void };
  const [values, setValues] = useState<BannerFormValues>(initialValues ?? EMPTY_FORM);

  const updateField =
    (field: "imageUrl" | "title" | "linkUrl" | "startAt" | "endAt" | "sortOrder") =>
    (e: ChangeEvent<HTMLInputElement>) =>
      setValues((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!values.imageUrl.trim() || !values.title.trim()) {
      toast.error("이미지 URL과 문구를 입력해주세요.");
      return;
    }
    if (!values.startAt || !values.endAt) {
      toast.error("노출 기간을 입력해주세요.");
      return;
    }
    if (values.startAt > values.endAt) {
      toast.error("시작 시각이 종료 시각보다 늦을 수 없습니다.");
      return;
    }
    onSubmit(values);
  };

  return (
    <form
      className="flex flex-col gap-3 rounded-xl border border-[var(--color-forest)]/10 bg-[var(--color-paper)] p-4"
      onSubmit={handleSubmit}
    >
      <FormField label="이미지 URL">
        <input
          type="text"
          value={values.imageUrl}
          onChange={updateField("imageUrl")}
          placeholder="https://cdn.example.com/banners/xxx.png"
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
      </FormField>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FormField label="문구">
          <input
            type="text"
            value={values.title}
            onChange={updateField("title")}
            placeholder="예: 정기구독 첫 달 무료"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="클릭 시 이동 경로 (선택)">
          <input
            type="text"
            value={values.linkUrl}
            onChange={updateField("linkUrl")}
            placeholder="/mypage?tab=addresses"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <FormField label="시작 시각">
          <input
            type="datetime-local"
            value={values.startAt}
            onChange={updateField("startAt")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="종료 시각">
          <input
            type="datetime-local"
            value={values.endAt}
            onChange={updateField("endAt")}
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

      <label className="flex items-center gap-2 text-sm text-[var(--color-ink)] opacity-80">
        <input
          type="checkbox"
          checked={values.active}
          onChange={(e) => setValues((prev) => ({ ...prev, active: e.target.checked }))}
        />
        활성화
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

interface BannerItemProps {
  banner: SubscriptionBannerResponse;
  onEdit: () => void;
  onDeactivate: () => void;
  isBusy: boolean;
}

function BannerItem({ banner, onEdit, onDeactivate, isBusy }: BannerItemProps) {
  const expired = isExpired(banner);
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4">
      <div className="flex items-center gap-2">
        {!banner.active && (
          <span className="rounded-full bg-[var(--color-coral)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-coral)]">
            비활성
          </span>
        )}
        {expired && (
          <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
            기간 종료
          </span>
        )}
      </div>
      <img
        src={banner.imageUrl}
        alt={banner.title}
        className="h-24 w-full rounded-lg object-cover"
        onError={(e) => {
          e.currentTarget.style.display = "none";
        }}
      />
      <p className="text-sm font-semibold text-[var(--color-ink)]">{banner.title}</p>
      {banner.linkUrl && (
        <p className="text-sm text-[var(--color-ink)] opacity-60">이동: {banner.linkUrl}</p>
      )}
      <p className="text-sm text-[var(--color-ink)] opacity-50">
        {banner.startAt.slice(0, 16).replace("T", " ")} ~ {banner.endAt.slice(0, 16).replace("T", " ")}
        {" · 순서 "}
        {banner.sortOrder}
      </p>

      <div className="mt-1 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={onEdit}
          className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)]"
        >
          수정
        </button>
        {banner.active && (
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
