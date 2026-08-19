import { useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Modal from "../../components/Modal.jsx";
import { useToast } from "../../components/Toast.jsx";
import { createCoupon, getAdminCoupons, updateCoupon } from "../../api/admin/coupons.ts";
import type { CouponResponse } from "../../api/types.ts";

const COUPONS_QUERY_KEY = ["admin", "coupons"];

interface CouponFormValues {
  couponCode: string;
  couponName: string;
  discountAmount: string;
  minimumOrderAmount: string;
  expiresAt: string;
}

const EMPTY_FORM: CouponFormValues = {
  couponCode: "",
  couponName: "",
  discountAmount: "",
  minimumOrderAmount: "0",
  expiresAt: "",
};

// "2026-12-31T23:59:59" -> "2026-12-31T23:59" (datetime-local input이 받는 형태)
function toDatetimeLocalValue(isoLike: string): string {
  return isoLike.slice(0, 16);
}

// "2026-12-31T23:59" (datetime-local 값) -> "2026-12-31T23:59:00" (백엔드 LocalDateTime 형태)
function toLocalDateTime(datetimeLocalValue: string): string {
  return datetimeLocalValue.length === 16 ? `${datetimeLocalValue}:00` : datetimeLocalValue;
}

function isExpired(coupon: CouponResponse): boolean {
  return new Date(coupon.expiresAt).getTime() <= Date.now();
}

export default function AdminCouponsPage() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: COUPONS_QUERY_KEY });
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const {
    data: coupons,
    isPending,
    isError,
  } = useQuery({ queryKey: COUPONS_QUERY_KEY, queryFn: getAdminCoupons });

  const create = useMutation({
    mutationFn: createCoupon,
    onSuccess: () => {
      invalidate();
      toast.success("쿠폰을 등록했습니다");
    },
    onError: () => toast.error("쿠폰 등록에 실패했습니다 (코드 중복 여부를 확인해주세요)"),
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Parameters<typeof updateCoupon>[1] }) =>
      updateCoupon(id, body),
    onSuccess: () => {
      invalidate();
      toast.success("쿠폰을 수정했습니다");
    },
    onError: () => toast.error("쿠폰 수정에 실패했습니다"),
  });

  const [editingCoupon, setEditingCoupon] = useState<CouponResponse | null>(null);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">🎟️ 쿠폰 정책 관리</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          쿠폰 코드/할인금액/최소주문금액/만료일을 등록·수정합니다. 삭제 API는 없습니다 — 만료일을
          지난 시각으로 수정하면 그 쿠폰은 더 이상 발급/사용되지 않습니다.
        </p>
        <CouponForm
          onSubmit={(values) =>
            create.mutate({
              couponCode: values.couponCode,
              couponName: values.couponName,
              discountAmount: Number(values.discountAmount),
              minimumOrderAmount: Number(values.minimumOrderAmount),
              expiresAt: toLocalDateTime(values.expiresAt),
            })
          }
          isSubmitting={create.isPending}
        />
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">전체 쿠폰</h2>

        {isPending ? (
          <div className="grid gap-3 sm:grid-cols-2">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer h-24 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            쿠폰 목록을 불러오지 못했습니다.
          </p>
        ) : !coupons || coupons.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            등록된 쿠폰이 없습니다.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {coupons.map((coupon) => (
              <CouponItem
                key={coupon.couponId}
                coupon={coupon}
                onEdit={() => setEditingCoupon(coupon)}
              />
            ))}
          </ul>
        )}
      </section>

      <Modal
        isOpen={editingCoupon !== null}
        onClose={() => setEditingCoupon(null)}
        title="쿠폰 정책 수정"
        footer={null}
      >
        {editingCoupon && (
          <CouponForm
            initialValues={{
              couponCode: editingCoupon.couponCode,
              couponName: editingCoupon.couponName,
              discountAmount: String(editingCoupon.discountAmount),
              minimumOrderAmount: String(editingCoupon.minimumOrderAmount),
              expiresAt: toDatetimeLocalValue(editingCoupon.expiresAt),
            }}
            submitLabel="수정 완료"
            isSubmitting={update.isPending}
            onSubmit={(values) => {
              update.mutate(
                {
                  id: editingCoupon.couponId,
                  body: {
                    couponName: values.couponName,
                    discountAmount: Number(values.discountAmount),
                    minimumOrderAmount: Number(values.minimumOrderAmount),
                    expiresAt: toLocalDateTime(values.expiresAt),
                  },
                },
                { onSuccess: () => setEditingCoupon(null) },
              );
            }}
          />
        )}
      </Modal>
    </main>
  );
}

interface CouponFormProps {
  initialValues?: CouponFormValues;
  submitLabel?: string;
  isSubmitting: boolean;
  onSubmit: (values: CouponFormValues) => void;
}

function CouponForm({
  initialValues,
  submitLabel = "쿠폰 등록",
  isSubmitting,
  onSubmit,
}: CouponFormProps) {
  const toast = useToast() as { error: (message: string) => void };
  // 수정 모드에서는 couponCode 를 바꿀 수 없다 — 발급 식별자라 PATCH 대상에서 제외된다.
  const isEditing = initialValues !== undefined;
  const [values, setValues] = useState<CouponFormValues>(initialValues ?? EMPTY_FORM);

  const updateField = (field: keyof CouponFormValues) => (e: ChangeEvent<HTMLInputElement>) =>
    setValues((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!values.couponCode.trim() || !values.couponName.trim()) {
      toast.error("쿠폰 코드와 쿠폰명을 입력해주세요.");
      return;
    }
    if (!values.discountAmount || Number(values.discountAmount) <= 0) {
      toast.error("할인금액은 0보다 커야 합니다.");
      return;
    }
    if (!values.expiresAt) {
      toast.error("만료일시를 입력해주세요.");
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
        <FormField label="쿠폰 코드">
          <input
            type="text"
            value={values.couponCode}
            onChange={updateField("couponCode")}
            disabled={isEditing}
            placeholder="예: WELCOME3000"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
          />
        </FormField>
        <FormField label="쿠폰명">
          <input
            type="text"
            value={values.couponName}
            onChange={updateField("couponName")}
            placeholder="예: 신규가입 3천원 할인"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <FormField label="할인금액(원)">
          <input
            type="number"
            min={1}
            value={values.discountAmount}
            onChange={updateField("discountAmount")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="최소주문금액(원)">
          <input
            type="number"
            min={0}
            value={values.minimumOrderAmount}
            onChange={updateField("minimumOrderAmount")}
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="만료일시">
          <input
            type="datetime-local"
            value={values.expiresAt}
            onChange={updateField("expiresAt")}
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

interface CouponItemProps {
  coupon: CouponResponse;
  onEdit: () => void;
}

function CouponItem({ coupon, onEdit }: CouponItemProps) {
  const expired = isExpired(coupon);
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4">
      <div className="flex items-center gap-2">
        <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
          {coupon.couponCode}
        </span>
        {expired && (
          <span className="rounded-full bg-[var(--color-coral)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-coral)]">
            만료
          </span>
        )}
      </div>
      <p className="text-sm font-semibold text-[var(--color-ink)]">{coupon.couponName}</p>
      <p className="text-sm text-[var(--color-ink)] opacity-70">
        {coupon.discountAmount.toLocaleString()}원 할인 · 최소주문{" "}
        {coupon.minimumOrderAmount.toLocaleString()}원
      </p>
      <p className="text-sm text-[var(--color-ink)] opacity-50">
        만료일 {coupon.expiresAt.slice(0, 16).replace("T", " ")}
      </p>

      <div className="mt-1 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={onEdit}
          className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)]"
        >
          수정
        </button>
      </div>
    </li>
  );
}
