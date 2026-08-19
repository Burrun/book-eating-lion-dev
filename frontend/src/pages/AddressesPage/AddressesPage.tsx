import { useRef, useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search } from "lucide-react";
import Modal from "../../components/Modal.jsx";
import { useToast } from "../../components/Toast.jsx";
import {
  getMyAddresses,
  createAddress,
  updateAddress,
  deleteAddress,
} from "../../api/addresses.ts";
import { openDaumPostcode } from "../../utils/daumPostcode.js";
import type { Address } from "../../types/address.ts";

const ADDRESSES_QUERY_KEY = ["addresses"];

interface AddressFormValues {
  recipientName: string;
  phoneNumber: string;
  zipcode: string;
  address: string;
  detailAddress: string;
}

const EMPTY_FORM: AddressFormValues = {
  recipientName: "",
  phoneNumber: "",
  zipcode: "",
  address: "",
  detailAddress: "",
};

export default function AddressesPage() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ADDRESSES_QUERY_KEY });
  // Toast.jsx가 .jsx라 checkJs:false — CardsPage.tsx/ProductDetailPage.tsx와 동일한 이유로 여기서만 타입 명시.
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const {
    data: addresses,
    isPending,
    isError,
  } = useQuery({
    queryKey: ADDRESSES_QUERY_KEY,
    queryFn: getMyAddresses,
  });

  const create = useMutation({
    mutationFn: createAddress,
    onSuccess: () => {
      invalidate();
      toast.success("배송지를 등록했습니다");
    },
    onError: () => toast.error("배송지 등록에 실패했습니다"),
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Parameters<typeof updateAddress>[1] }) =>
      updateAddress(id, body),
    onSuccess: () => {
      invalidate();
      toast.success("배송지를 수정했습니다");
    },
    onError: () => toast.error("배송지 수정에 실패했습니다"),
  });

  const remove = useMutation({
    mutationFn: deleteAddress,
    onSuccess: () => {
      invalidate();
      toast.success("배송지를 삭제했습니다");
    },
    onError: () => toast.error("배송지 삭제에 실패했습니다"),
  });

  const [editingAddress, setEditingAddress] = useState<Address | null>(null);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">📮 배송지 관리</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          자주 쓰는 배송지를 저장해두면 결제 시 바로 선택할 수 있어요.
        </p>
        <AddressForm
          onSubmit={(values) => create.mutate({ ...values, isDefault: false })}
          isSubmitting={create.isPending}
        />
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">저장된 배송지</h2>

        {isPending ? (
          <div className="grid gap-3 sm:grid-cols-2">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer h-28 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            배송지 목록을 불러오지 못했습니다.
          </p>
        ) : !addresses || addresses.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            등록된 배송지가 없습니다. 위에서 새 배송지를 등록해 보세요.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {addresses.map((addr) => (
              <AddressItem
                key={addr.id}
                address={addr}
                onEdit={() => setEditingAddress(addr)}
                onSetDefault={() => update.mutate({ id: addr.id, body: { isDefault: true } })}
                onDelete={() => remove.mutate(addr.id)}
                isBusy={
                  (update.isPending && update.variables?.id === addr.id) ||
                  (remove.isPending && remove.variables === addr.id)
                }
              />
            ))}
          </ul>
        )}
      </section>

      <Modal
        isOpen={editingAddress !== null}
        onClose={() => setEditingAddress(null)}
        title="배송지 수정"
        footer={null}
      >
        {editingAddress && (
          <AddressForm
            initialValues={{
              recipientName: editingAddress.recipientName,
              phoneNumber: editingAddress.phoneNumber,
              zipcode: editingAddress.zipcode,
              address: editingAddress.address,
              detailAddress: editingAddress.detailAddress ?? "",
            }}
            submitLabel="수정 완료"
            isSubmitting={update.isPending}
            onSubmit={(values) => {
              update.mutate(
                { id: editingAddress.id, body: values },
                { onSuccess: () => setEditingAddress(null) },
              );
            }}
          />
        )}
      </Modal>
    </main>
  );
}

interface AddressFormProps {
  initialValues?: AddressFormValues;
  submitLabel?: string;
  isSubmitting: boolean;
  onSubmit: (values: AddressFormValues) => void;
}

function AddressForm({
  initialValues,
  submitLabel = "배송지 등록",
  isSubmitting,
  onSubmit,
}: AddressFormProps) {
  const toast = useToast() as { error: (message: string) => void };
  const detailRef = useRef<HTMLInputElement>(null);
  const [values, setValues] = useState<AddressFormValues>(initialValues ?? EMPTY_FORM);

  const updateField = (field: keyof AddressFormValues) => (e: ChangeEvent<HTMLInputElement>) =>
    setValues((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSearchAddress = async () => {
    try {
      await openDaumPostcode(({ zipcode, address }: { zipcode: string; address: string }) => {
        setValues((prev) => ({ ...prev, zipcode, address }));
        detailRef.current?.focus();
      });
    } catch {
      toast.error("우편번호 검색을 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (
      !values.recipientName.trim() ||
      !values.phoneNumber.trim() ||
      !values.zipcode ||
      !values.address
    ) {
      toast.error("받는 분, 연락처, 주소를 입력해주세요.");
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
        <FormField label="받는 분">
          <input
            type="text"
            value={values.recipientName}
            onChange={updateField("recipientName")}
            placeholder="이름"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
        <FormField label="연락처">
          <input
            type="tel"
            value={values.phoneNumber}
            onChange={updateField("phoneNumber")}
            placeholder="010-0000-0000"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </FormField>
      </div>

      <FormField label="우편번호">
        <div className="flex gap-2">
          <input
            type="text"
            value={values.zipcode}
            readOnly
            placeholder="우편번호 검색을 눌러주세요"
            className="w-full rounded-lg border border-[var(--color-forest)]/20 bg-[var(--color-forest)]/5 px-3.5 py-2.5 text-sm text-[var(--color-ink)]/70 focus:outline-none"
          />
          <button
            type="button"
            onClick={handleSearchAddress}
            className="flex shrink-0 items-center gap-1 rounded-lg border-2 border-[var(--color-forest)] px-4 py-2.5 text-sm font-semibold text-[var(--color-forest)] transition hover:bg-[var(--color-forest)] hover:text-[var(--color-paper)]"
          >
            <Search size={15} />
            검색
          </button>
        </div>
      </FormField>

      <FormField label="주소">
        <input
          type="text"
          value={values.address}
          readOnly
          placeholder="우편번호 검색 시 자동 입력됩니다"
          className="w-full rounded-lg border border-[var(--color-forest)]/20 bg-[var(--color-forest)]/5 px-3.5 py-2.5 text-sm text-[var(--color-ink)]/70 focus:outline-none"
        />
      </FormField>

      <FormField label="상세 주소">
        <input
          ref={detailRef}
          type="text"
          value={values.detailAddress}
          onChange={updateField("detailAddress")}
          placeholder="동/호수 등 상세 주소"
          className="w-full rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
      </FormField>

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

interface AddressItemProps {
  address: Address;
  onEdit: () => void;
  onSetDefault: () => void;
  onDelete: () => void;
  isBusy: boolean;
}

function AddressItem({ address, onEdit, onSetDefault, onDelete, isBusy }: AddressItemProps) {
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4">
      <div className="flex items-center gap-2">
        <span className="text-sm font-semibold text-[var(--color-ink)]">
          {address.recipientName}
        </span>
        {address.isDefault && (
          <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
            기본 배송지
          </span>
        )}
      </div>
      <p className="text-sm text-[var(--color-ink)] opacity-70">
        ({address.zipcode}) {address.address} {address.detailAddress}
      </p>
      <p className="text-sm text-[var(--color-ink)] opacity-50">{address.phoneNumber}</p>

      <div className="mt-1 flex flex-wrap gap-2">
        {!address.isDefault && (
          <button
            type="button"
            disabled={isBusy}
            onClick={onSetDefault}
            className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)] disabled:opacity-40"
          >
            기본 배송지로 설정
          </button>
        )}
        <button
          type="button"
          disabled={isBusy}
          onClick={onEdit}
          className="rounded-lg border border-[var(--color-forest)]/20 px-3 py-1.5 text-xs font-semibold transition hover:bg-[var(--color-paper)] disabled:opacity-40"
        >
          수정
        </button>
        <button
          type="button"
          disabled={isBusy}
          onClick={onDelete}
          className="rounded-lg border border-[var(--color-coral)]/30 px-3 py-1.5 text-xs font-semibold text-[var(--color-coral)] transition hover:bg-[var(--color-coral)]/10 disabled:opacity-40"
        >
          삭제
        </button>
      </div>
    </li>
  );
}
