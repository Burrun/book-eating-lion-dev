import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Minus, Plus, Trash2, Ticket, BookOpen } from "lucide-react";
import Button from "../components/Button.jsx";
import Skeleton from "../components/Skeleton.jsx";
import { fetchCartItems, fetchCartBenefits, updateQuantity, removeFromCart } from "../api/cart.js";

const FREE_SHIPPING_THRESHOLD = 30000;

export default function Cart() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [benefits, setBenefits] = useState({ availableCoupon: null, availablePoints: 0 });
  const [couponApplied, setCouponApplied] = useState(false);
  const [pointsUsed, setPointsUsed] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    Promise.all([fetchCartItems(), fetchCartBenefits()]).then(([fetchedItems, fetchedBenefits]) => {
      if (ignore) return;
      setItems(fetchedItems);
      setSelectedIds(new Set(fetchedItems.map((i) => i.id)));
      setBenefits(fetchedBenefits);
      setIsLoading(false);
    });
    return () => {
      ignore = true;
    };
  }, []);

  const { availableCoupon, availablePoints } = benefits;
  const selectedItems = items.filter((item) => selectedIds.has(item.id));
  const allSelected = items.length > 0 && selectedIds.size === items.length;

  const subtotal = selectedItems.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const shippingFee = useMemo(() => {
    if (selectedItems.length === 0) return 0;
    if (subtotal >= FREE_SHIPPING_THRESHOLD) return 0;
    return Math.max(...selectedItems.map((item) => item.shippingFee));
  }, [selectedItems, subtotal]);
  const couponDiscount = couponApplied && availableCoupon ? Math.min(availableCoupon.discount, subtotal) : 0;
  const finalTotal = Math.max(subtotal + shippingFee - couponDiscount - pointsUsed, 0);

  const toggleSelectAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(items.map((i) => i.id)));
  };

  const toggleSelect = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const changeQuantity = (id, delta) => {
    const target = items.find((item) => item.id === id);
    if (!target) return;
    const newQuantity = Math.max(1, target.quantity + delta);
    setItems((prev) => prev.map((item) => (item.id === id ? { ...item, quantity: newQuantity } : item)));
    // 낙관적으로 화면은 먼저 갱신하고, 저장소 반영 실패는 조용히 무시한다
    // (백엔드 cart 모듈 미구현으로 로그인 상태의 실API 호출은 항상 실패할 수 있음 — BOO-23 TODO).
    updateQuantity(id, newQuantity).catch(() => {});
  };

  const removeItem = (id) => {
    setItems((prev) => prev.filter((item) => item.id !== id));
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
    removeFromCart(id).catch(() => {});
  };

  const removeSelected = () => {
    const idsToRemove = [...selectedIds];
    setItems((prev) => prev.filter((item) => !selectedIds.has(item.id)));
    setSelectedIds(new Set());
    idsToRemove.forEach((id) => removeFromCart(id).catch(() => {}));
  };

  const togglePoints = () => {
    setPointsUsed((prev) => (prev > 0 ? 0 : Math.min(availablePoints, subtotal + shippingFee - couponDiscount)));
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <h1 className="font-display mb-6 text-2xl text-[var(--color-forest)]">
        장바구니 <span className="text-[var(--color-ink)]/50">({items.length}건)</span>
      </h1>

      {isLoading ? (
        <CartSkeleton />
      ) : items.length === 0 ? (
        <EmptyCart />
      ) : (
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-[1fr_360px]">
          <div className="flex flex-col gap-4">
            {/* 전체선택 / 선택삭제 */}
            <div className="flex items-center justify-between px-1">
              <label className="flex items-center gap-2 text-sm text-[var(--color-ink)]">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={toggleSelectAll}
                  className="h-5 w-5 accent-[var(--color-forest)]"
                />
                전체 선택 ({selectedIds.size}/{items.length})
              </label>
              <button
                type="button"
                onClick={removeSelected}
                disabled={selectedIds.size === 0}
                className="flex items-center gap-1 text-sm text-[var(--color-coral)] transition-opacity hover:underline disabled:opacity-30 disabled:hover:no-underline"
              >
                <Trash2 size={14} />
                선택 삭제
              </button>
            </div>

            {/* 상품 목록 */}
            {items.map((item) => (
              <CartItemRow
                key={item.id}
                item={item}
                selected={selectedIds.has(item.id)}
                onToggleSelect={() => toggleSelect(item.id)}
                onChangeQuantity={(delta) => changeQuantity(item.id, delta)}
                onRemove={() => removeItem(item.id)}
              />
            ))}

            {/* 쿠폰/포인트 - 티어오프 영수증 느낌 */}
            <div className="relative mt-2 rounded-2xl border-2 border-dashed border-[var(--color-honey)]/60 bg-[var(--color-honey)]/10 p-5 shadow-[0_2px_10px_rgba(242,169,59,0.15)]">
              <span className="absolute top-1/2 -left-2.5 h-5 w-5 -translate-y-1/2 rounded-full bg-[var(--color-paper)]" />
              <span className="absolute top-1/2 -right-2.5 h-5 w-5 -translate-y-1/2 rounded-full bg-[var(--color-paper)]" />

              <div className="mb-3 flex items-center gap-2 text-[var(--color-forest)]">
                <Ticket size={18} />
                <h2 className="font-display text-base">쿠폰 · 포인트 할인 적용</h2>
              </div>

              <div className="flex flex-col gap-3">
                <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-white/70 px-4 py-3">
                  <p className="text-sm text-[var(--color-ink)]">
                    적용 가능 쿠폰:{" "}
                    <span className="font-medium">{availableCoupon?.label}</span>
                  </p>
                  <Button
                    variant={couponApplied ? "secondary" : "primary"}
                    size="sm"
                    shimmer={false}
                    onClick={() => setCouponApplied((prev) => !prev)}
                  >
                    {couponApplied ? "적용 취소" : "쿠폰 적용"}
                  </Button>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-white/70 px-4 py-3">
                  <p className="text-sm text-[var(--color-ink)]">
                    보유 포인트: <span className="font-medium">{availablePoints.toLocaleString()}P</span>
                    <span className="ml-1 text-[var(--color-ink)]/50">(전액 사용 가능)</span>
                  </p>
                  <Button
                    variant={pointsUsed > 0 ? "secondary" : "primary"}
                    size="sm"
                    shimmer={false}
                    onClick={togglePoints}
                  >
                    {pointsUsed > 0 ? "사용 취소" : "전액 사용"}
                  </Button>
                </div>
              </div>
            </div>
          </div>

          {/* 결제 금액 요약 */}
          <aside className="lg:sticky lg:top-24 lg:self-start">
            <div className="rounded-2xl bg-white p-6 shadow-[0_10px_30px_rgba(27,59,54,0.10)]">
              <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">
                주문 결제 금액 요약
              </h2>
              <dl className="space-y-2.5 text-sm">
                <Row label="총 상품 금액" value={`${subtotal.toLocaleString()}원`} />
                <Row label="배송비" value={shippingFee > 0 ? `+${shippingFee.toLocaleString()}원` : "무료"} />
                <Row
                  label="쿠폰 할인"
                  value={couponDiscount > 0 ? `-${couponDiscount.toLocaleString()}원` : "-"}
                  tone={couponDiscount > 0 ? "coral" : undefined}
                />
                <Row
                  label="포인트 사용"
                  value={pointsUsed > 0 ? `-${pointsUsed.toLocaleString()}원` : "-"}
                  tone={pointsUsed > 0 ? "coral" : undefined}
                />
              </dl>

              <div className="mt-4 flex items-baseline justify-between border-t border-[var(--color-forest)]/10 pt-4">
                <span className="text-sm text-[var(--color-ink)]/70">최종 결제 예정 금액</span>
                <span className="font-display text-2xl text-[var(--color-coral)]">
                  {finalTotal.toLocaleString()}원
                </span>
              </div>

              <Button
                variant="primary"
                size="lg"
                fullWidth
                disabled={selectedItems.length === 0}
                onClick={() => navigate("/checkout")}
                className="mt-5"
              >
                주문 / 결제하기
              </Button>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}

function Row({ label, value, tone }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-[var(--color-ink)]/70">{label}</dt>
      <dd className={tone === "coral" ? "text-[var(--color-coral)]" : "text-[var(--color-ink)]"}>{value}</dd>
    </div>
  );
}

function CartItemRow({ item, selected, onToggleSelect, onChangeQuantity, onRemove }) {
  return (
    <div className="flex gap-4 rounded-2xl bg-white p-4 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <input
        type="checkbox"
        checked={selected}
        onChange={onToggleSelect}
        className="mt-1 h-5 w-5 shrink-0 accent-[var(--color-forest)]"
      />

      <div className="flex h-24 w-[72px] shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[var(--color-forest)]/5">
        {item.coverUrl ? (
          <img src={item.coverUrl} alt={item.title} className="h-full w-full object-cover" />
        ) : (
          <BookOpen size={24} className="text-[var(--color-forest)]/25" strokeWidth={1.5} />
        )}
      </div>

      <div className="flex flex-1 flex-col gap-1.5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-sm font-medium text-[var(--color-ink)]">{item.title}</p>
            <div className="mt-1 flex flex-wrap items-center gap-1.5">
              {item.condition && (
                <span className="rounded-full border border-[var(--color-forest)]/30 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
                  중고 · {item.condition}급
                </span>
              )}
              <span className="text-sm text-[var(--color-ink)] opacity-70">
                {item.option ? `${item.option} · ` : ""}배송비 {item.shippingFee.toLocaleString()}원
              </span>
            </div>
          </div>
          <button
            type="button"
            aria-label="상품 삭제"
            onClick={onRemove}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[var(--color-coral)] transition-colors hover:bg-[var(--color-coral)]/10"
          >
            <Trash2 size={16} />
          </button>
        </div>

        <div className="mt-1.5 flex items-center justify-between">
          <div className="flex items-center overflow-hidden rounded-full border border-[var(--color-forest)]/20">
            <button
              type="button"
              aria-label="수량 감소"
              onClick={() => onChangeQuantity(-1)}
              className="flex h-8 w-8 items-center justify-center text-[var(--color-forest)] transition-colors hover:bg-[var(--color-honey)]/25"
            >
              <Minus size={14} />
            </button>
            <span className="w-8 text-center text-sm font-medium text-[var(--color-ink)]">
              {item.quantity}
            </span>
            <button
              type="button"
              aria-label="수량 증가"
              onClick={() => onChangeQuantity(1)}
              className="flex h-8 w-8 items-center justify-center text-[var(--color-forest)] transition-colors hover:bg-[var(--color-honey)]/25"
            >
              <Plus size={14} />
            </button>
          </div>
          <p className="font-display text-lg text-[var(--color-ink)]">
            {(item.price * item.quantity).toLocaleString()}원
          </p>
        </div>
      </div>
    </div>
  );
}

function EmptyCart() {
  return (
    <div className="flex flex-col items-center gap-4 rounded-2xl bg-white py-20 text-center shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-forest)]/5 text-[var(--color-forest)]/30">
        <BookOpen size={28} strokeWidth={1.5} />
      </div>
      <p className="text-[var(--color-ink)] opacity-70">장바구니가 비어있어요</p>
      <Link to="/">
        <Button variant="primary">책 구경하러 가기</Button>
      </Link>
    </div>
  );
}

function CartSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-[1fr_360px]">
      <div className="flex flex-col gap-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex gap-4 rounded-2xl bg-white p-4 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
            <Skeleton variant="rectangular" className="h-24 w-[72px] shrink-0" />
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton variant="text" className="w-2/3" />
              <Skeleton variant="text" className="w-1/3" />
              <Skeleton variant="text" className="mt-2 h-8 w-1/4" />
            </div>
          </div>
        ))}
      </div>
      <Skeleton variant="rectangular" className="h-80 w-full" />
    </div>
  );
}
