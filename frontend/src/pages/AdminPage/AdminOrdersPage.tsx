import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useToast } from "../../components/Toast.jsx";
import { getAdminOrders, updateOrderDeliveryStatus } from "../../api/admin/orders.ts";
import type { AdminOrderSummaryResponse, DeliveryStatus, OrderStatus } from "../../api/types.ts";

const PAGE_SIZE = 20;

const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "결제 대기",
  PAID: "결제 완료",
  CANCELLED: "취소됨",
  RETURN_REQUESTED: "반품 신청",
  REFUNDED: "환불 완료",
};

const DELIVERY_STATUS_LABEL: Record<DeliveryStatus, string> = {
  PENDING: "배송 대기",
  SHIPPED: "배송 시작",
  IN_TRANSIT: "배송 중",
  DELIVERED: "배송 완료",
};

// PENDING→SHIPPED→IN_TRANSIT→DELIVERED. 서버(Delivery.updateStatus)가 한 단계 전이만
// 강제하므로, 프론트에서도 건너뛰기/역행을 선택할 수 없게 다음 단계 하나만 계산해 보여준다.
const NEXT_STATUS: Record<DeliveryStatus, DeliveryStatus | null> = {
  PENDING: "SHIPPED",
  SHIPPED: "IN_TRANSIT",
  IN_TRANSIT: "DELIVERED",
  DELIVERED: null,
};

export default function AdminOrdersPage() {
  const queryClient = useQueryClient();
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const [statusFilter, setStatusFilter] = useState<OrderStatus | "">("");
  const [page, setPage] = useState(0);

  const queryKey = ["admin", "orders", statusFilter, page];

  const {
    data: orderPage,
    isPending,
    isError,
  } = useQuery({
    queryKey,
    queryFn: () =>
      getAdminOrders({ status: statusFilter || undefined, page, size: PAGE_SIZE }),
  });

  const advance = useMutation({
    mutationFn: ({ orderId, status }: { orderId: number; status: DeliveryStatus }) =>
      updateOrderDeliveryStatus(orderId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "orders"] });
      toast.success("배송 상태를 변경했습니다");
    },
    onError: () => toast.error("배송 상태 변경에 실패했습니다"),
  });

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">📦 주문/배송 관리</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          전체 주문을 조회하고 배송 상태를 다음 단계로 진행시킵니다. 건너뛰기·되돌리기는 지원하지
          않습니다(PENDING→SHIPPED→IN_TRANSIT→DELIVERED 순서만 허용).
        </p>

        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value as OrderStatus | "");
            setPage(0);
          }}
          className="rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        >
          <option value="">전체 주문상태</option>
          {Object.entries(ORDER_STATUS_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        {isPending ? (
          <div className="flex flex-col gap-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="skeleton-shimmer h-16 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            주문 목록을 불러오지 못했습니다.
          </p>
        ) : !orderPage || orderPage.content.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            조건에 맞는 주문이 없습니다.
          </p>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead>
                  <tr className="border-b border-[var(--color-forest)]/10 text-[var(--color-ink)] opacity-60">
                    <th className="py-2 pr-3 font-medium">주문번호</th>
                    <th className="py-2 pr-3 font-medium">받는 분</th>
                    <th className="py-2 pr-3 font-medium">주문상태</th>
                    <th className="py-2 pr-3 font-medium">배송상태</th>
                    <th className="py-2 pr-3 font-medium">금액</th>
                    <th className="py-2 pr-3 font-medium" />
                  </tr>
                </thead>
                <tbody>
                  {orderPage.content.map((order) => (
                    <OrderRow
                      key={order.orderId}
                      order={order}
                      onAdvance={(status) => advance.mutate({ orderId: order.orderId, status })}
                      isBusy={advance.isPending && advance.variables?.orderId === order.orderId}
                    />
                  ))}
                </tbody>
              </table>
            </div>

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
                {orderPage.number + 1} / {orderPage.totalPages}
              </span>
              <button
                type="button"
                disabled={orderPage.last}
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

interface OrderRowProps {
  order: AdminOrderSummaryResponse;
  onAdvance: (status: DeliveryStatus) => void;
  isBusy: boolean;
}

function OrderRow({ order, onAdvance, isBusy }: OrderRowProps) {
  const deliveryStatus = order.deliveryStatus;
  const nextStatus = deliveryStatus ? NEXT_STATUS[deliveryStatus] : null;

  return (
    <tr className="border-b border-[var(--color-forest)]/5">
      <td className="py-3 pr-3 font-medium text-[var(--color-ink)]">#{order.orderId}</td>
      <td className="py-3 pr-3 text-[var(--color-ink)] opacity-80">{order.recipientName}</td>
      <td className="py-3 pr-3">
        <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
          {ORDER_STATUS_LABEL[order.orderStatus]}
        </span>
      </td>
      <td className="py-3 pr-3">
        {deliveryStatus ? (
          <span className="rounded-full bg-[var(--color-honey)]/15 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
            {DELIVERY_STATUS_LABEL[deliveryStatus]}
          </span>
        ) : (
          <span className="text-[11px] text-[var(--color-ink)] opacity-40">결제 확정 전</span>
        )}
      </td>
      <td className="py-3 pr-3 text-[var(--color-ink)] opacity-80">{order.totalAmount.toLocaleString()}원</td>
      <td className="py-3 pr-3 text-right">
        {nextStatus && (
          <button
            type="button"
            disabled={isBusy}
            onClick={() => onAdvance(nextStatus)}
            className="rounded-lg bg-[var(--color-honey)] px-3 py-1.5 text-xs font-semibold text-[var(--color-forest)] shadow-sm transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isBusy ? "변경 중..." : `${DELIVERY_STATUS_LABEL[nextStatus]}(으)로`}
          </button>
        )}
      </td>
    </tr>
  );
}
