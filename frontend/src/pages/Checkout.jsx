import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Search, Wallet, CreditCard, PawPrint } from "lucide-react";
import Button from "../components/Button.jsx";
import Modal from "../components/Modal.jsx";
import Skeleton from "../components/Skeleton.jsx";
import { useToast } from "../components/Toast.jsx";
import { getMyCards } from "../api/cards.ts";
import { getMyAddresses } from "../api/addresses.ts";
import { fetchCoupons } from "../api/mypage.js";
import { createOrder } from "../api/orders.js";
import { openDaumPostcode } from "../utils/daumPostcode.js";

const FREE_SHIPPING_THRESHOLD = 30000;

const REQUIRED_FIELDS = [
  { key: "receiver", label: "받는 분 이름" },
  { key: "phone", label: "연락처" },
  { key: "zipcode", label: "우편번호" },
  { key: "address", label: "주소" },
  { key: "addressDetail", label: "상세 주소" },
];

const CARD_STATUS_LABEL = {
  ACTIVE: "사용중",
  SUSPENDED: "정지됨",
  CLOSED: "해지됨",
};

const PAYMENT_METHODS = [
  {
    // 백엔드 PaymentMethod enum: KAKAO_PAY.
    id: "KAKAO_PAY",
    label: "카카오페이",
    description: "카카오 계정으로 간편결제",
    icon: Wallet,
  },
  {
    // 결제 UI상 브랜드명은 "Saja Pay"이지만, 백엔드 PaymentMethod enum에서는 VIRTUAL_CARD로 매핑됩니다.
    id: "VIRTUAL_CARD",
    label: "카드 (Saja Pay)",
    description: "신용/체크카드 결제",
    icon: CreditCard,
  },
];

export default function Checkout() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const toast = useToast();
  const addressDetailRef = useRef(null);
  const [form, setForm] = useState({
    receiver: "",
    phone: "",
    zipcode: "",
    address: "",
    addressDetail: "",
    request: "문 앞에 놔주세요",
  });
  const [paymentMethod, setPaymentMethod] = useState(null);
  const [selectedCardId, setSelectedCardId] = useState(null);
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const [isPlacingOrder, setIsPlacingOrder] = useState(false);
  const [cards, setCards] = useState(null);
  const [addresses, setAddresses] = useState(null);
  const [coupons, setCoupons] = useState(null);
  // null이면 "새 배송지 입력" 모드. 값이 있으면 그 id의 저장된 배송지를 쓰는 중이다.
  const [selectedAddressId, setSelectedAddressId] = useState(null);
  // null이면 쿠폰 미사용.
  const [selectedCouponId, setSelectedCouponId] = useState(null);

  // Cart.jsx에서 "주문/결제하기" 클릭 시 선택된 상품 목록을 state로 넘겨준다.
  // 새로고침 등으로 state 없이 직접 들어오면 아래 렌더링에서 안내 후 장바구니로 돌려보낸다.
  const checkoutItems = location.state?.items ?? null;

  const applyAddress = (savedAddress) => {
    setSelectedAddressId(savedAddress.id);
    setForm((prev) => ({
      ...prev,
      receiver: savedAddress.recipientName,
      phone: savedAddress.phoneNumber,
      zipcode: savedAddress.zipcode,
      address: savedAddress.address,
      addressDetail: savedAddress.detailAddress ?? "",
    }));
  };

  useEffect(() => {
    let ignore = false;
    // 카드/배송지/쿠폰은 서로 독립적인 조회다 — 하나가 실패해도(예: 카드 미등록 상태에서의
    // 서버 오류) 나머지 둘로 화면은 계속 렌더링돼야 하므로 개별 catch로 빈 배열 폴백한다.
    Promise.all([
      getMyCards().catch(() => []),
      getMyAddresses().catch(() => []),
      fetchCoupons().catch(() => []),
    ]).then(([cardsData, addressesData, couponsData]) => {
      if (ignore) return;
      setCards(cardsData);
      setAddresses(addressesData);
      setCoupons(couponsData);
      // 저장된 배송지가 있으면 기본 배송지(없으면 첫 번째)로 미리 채워둔다.
      const preselected = addressesData.find((a) => a.isDefault) ?? addressesData[0];
      if (preselected) applyAddress(preselected);
    });
    return () => {
      ignore = true;
    };
  }, []);

  // Cart.jsx를 거치지 않고 새로고침/직접 진입한 경우 — 뭘 결제할지 알 수 없으므로 장바구니로 안내한다.
  if (!checkoutItems || checkoutItems.length === 0) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-3 px-4 py-24 text-center">
        <p className="text-4xl">🛒</p>
        <p className="text-sm text-[var(--color-ink)] opacity-60">
          장바구니에서 결제할 상품을 선택해주세요.
        </p>
        <Link
          to="/cart"
          className="rounded-full bg-[var(--color-forest)] px-5 py-2 text-sm font-semibold text-[var(--color-paper)] transition hover:bg-[var(--color-forest-light)]"
        >
          장바구니로 이동
        </Link>
      </div>
    );
  }

  if (cards === null || addresses === null || coupons === null) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <h1 className="font-display mb-6 text-2xl text-[var(--color-forest)]">주문 / 결제</h1>
        <CheckoutSkeleton />
      </div>
    );
  }

  const subtotal = checkoutItems.reduce((sum, item) => sum + item.price * item.quantity, 0);
  // 배송비는 백엔드 응답에 없는 클라이언트 전용 값이라 Cart.jsx와 동일한 규칙으로 계산한다.
  const shippingFee =
    subtotal >= FREE_SHIPPING_THRESHOLD
      ? 0
      : Math.max(...checkoutItems.map((item) => item.shippingFee ?? 3000));

  const selectedCoupon = coupons.find((c) => c.memberCouponId === selectedCouponId) ?? null;
  // 백엔드(OrderService.createOrder)도 할인을 상품 소계에서만 빼고 배송비는 건드리지 않는다.
  const couponDiscount = selectedCoupon ? Math.min(selectedCoupon.discountAmount, subtotal) : 0;
  const finalTotal = Math.max(subtotal - couponDiscount, 0) + shippingFee;

  const selectedMethod = PAYMENT_METHODS.find((m) => m.id === paymentMethod);

  const updateField = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handlePhoneChange = (e) => {
    const digits = e.target.value.replace(/\D/g, "").slice(0, 11);
    const formatted =
      digits.length < 4
        ? digits
        : digits.length < 8
          ? `${digits.slice(0, 3)}-${digits.slice(3)}`
          : `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
    setForm((prev) => ({ ...prev, phone: formatted }));
  };

  const validateAndOpenConfirm = () => {
    const missingField = REQUIRED_FIELDS.find(({ key }) => !form[key].trim());
    if (missingField) {
      toast.error(`${missingField.label}을(를) 입력해주세요.`);
      return;
    }
    if (!paymentMethod) {
      toast.error("결제 수단을 선택해주세요.");
      return;
    }
    if (paymentMethod === "VIRTUAL_CARD") {
      const chosenCard = cards?.find((card) => card.id === selectedCardId);
      if (!chosenCard) {
        toast.error("결제할 카드를 선택해주세요.");
        return;
      }
      if (chosenCard.availableLimit < finalTotal) {
        toast.error("카드 가용 한도가 부족합니다.");
        return;
      }
    }
    setIsConfirmOpen(true);
  };

  const handleSearchAddress = async () => {
    try {
      await openDaumPostcode(({ zipcode, address }) => {
        setForm((prev) => ({ ...prev, zipcode, address }));
        addressDetailRef.current?.focus();
      });
    } catch {
      toast.error("우편번호 검색을 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
    }
  };

  // 저장된 배송지 대신 새 배송지를 입력하는 모드로 전환한다.
  const handleUseNewAddress = () => {
    setSelectedAddressId(null);
    setForm((prev) => ({
      ...prev,
      receiver: "",
      phone: "",
      zipcode: "",
      address: "",
      addressDetail: "",
    }));
  };

  // 주문 생성(POST /api/orders). VIRTUAL_CARD는 이 호출 안에서 결제까지 끝나 orderStatus=PAID로
  // 응답하고(장바구니도 서버가 이미 비운다), KAKAO_PAY는 결제 준비(Ready)만 끝나
  // orderStatus=PENDING_PAYMENT + nextRedirectUrl로 응답한다 — 그 URL로 브라우저를 통째로
  // 리다이렉트시켜야 카카오 결제 페이지가 뜬다. 실제 승인(approve)은 카카오가 다시 우리
  // /payments/kakao/callback 으로 리다이렉트해줄 때 그 페이지에서 이뤄진다.
  const handleConfirmPayment = async () => {
    setIsPlacingOrder(true);
    try {
      const order = await createOrder({
        items: checkoutItems.map((item) => ({ bookId: item.bookId, quantity: item.quantity })),
        memberCouponId: selectedCouponId ? Number(selectedCouponId) : null,
        recipient: {
          name: form.receiver,
          phone: form.phone,
          postalCode: form.zipcode,
          // 백엔드 Recipient(addressDetail, deliveryRequest 둘 다 별도 필드로 받는다)에
          // 맞춰 합치지 않고 그대로 각각 실어 보낸다.
          address: form.address,
          addressDetail: form.addressDetail || null,
          deliveryRequest: form.request || null,
        },
        paymentMethod,
        cardId: paymentMethod === "VIRTUAL_CARD" ? Number(selectedCardId) : null,
      });

      if (paymentMethod === "KAKAO_PAY") {
        if (!order.nextRedirectUrl) {
          throw new Error("카카오페이 결제 페이지 URL을 받지 못했습니다.");
        }
        window.location.href = order.nextRedirectUrl;
        return;
      }

      // VIRTUAL_CARD는 createOrder 안에서 이미 서버가 장바구니를 비웠으므로 다시 지우지 않고
      // 캐시만 무효화한다(중복 삭제를 시도하면 이미 없는 아이템이라 404로 실패한다).
      queryClient.invalidateQueries({ queryKey: ["cart"] });
      // 구독권 주문이었으면 서버가 결제 확정과 함께 구독을 만들었다. 캐시를 안 비우면
      // 목록/상세의 구독 CTA가 한동안 "구독하기"로 남아 이미 산 사람에게 또 권한다.
      queryClient.invalidateQueries({ queryKey: ["mySubscription"] });

      setIsConfirmOpen(false);
      toast.success("결제가 완료되었습니다.");
      // 주문완료 전용 페이지가 아직 없어서 임시로 마이페이지 주문내역 탭으로 이동
      setTimeout(() => navigate("/mypage?tab=orders"), 1500);
    } catch (err) {
      console.error("주문 처리 실패:", err);
      const message =
        err.response?.data?.error?.message ??
        err.response?.data?.message ??
        "주문 처리에 실패했습니다. 잠시 후 다시 시도해주세요.";
      toast.error(message);
    } finally {
      setIsPlacingOrder(false);
    }
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <h1 className="font-display mb-6 text-2xl text-[var(--color-forest)]">주문 / 결제</h1>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[1fr_360px]">
        <div className="flex flex-col gap-6">
          {/* 배송지 입력 */}
          <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display text-lg text-[var(--color-forest)]">배송지 정보</h2>
              <Link
                to="/addresses"
                className="text-xs font-medium text-[var(--color-coral)] hover:underline"
              >
                배송지 관리
              </Link>
            </div>

            {addresses.length > 0 && (
              <div className="mb-4 grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                {addresses.map((savedAddress) => {
                  const isChosen = selectedAddressId === savedAddress.id;
                  return (
                    <button
                      key={savedAddress.id}
                      type="button"
                      onClick={() => applyAddress(savedAddress)}
                      className={`flex flex-col gap-1 rounded-xl border-2 p-3 text-left transition-colors ${
                        isChosen
                          ? "border-[var(--color-honey)] bg-[var(--color-honey)]/10"
                          : "border-[var(--color-forest)]/15 bg-white hover:border-[var(--color-honey)]/50"
                      }`}
                    >
                      <span className="flex items-center gap-2 text-sm font-medium text-[var(--color-ink)]">
                        {savedAddress.recipientName}
                        {savedAddress.isDefault && (
                          <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
                            기본
                          </span>
                        )}
                      </span>
                      <span className="text-xs text-[var(--color-ink)] opacity-60">
                        {savedAddress.address} {savedAddress.detailAddress}
                      </span>
                      <span className="text-xs text-[var(--color-ink)] opacity-50">
                        {savedAddress.phoneNumber}
                      </span>
                    </button>
                  );
                })}
                <button
                  type="button"
                  onClick={handleUseNewAddress}
                  className={`flex flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed p-3 text-sm font-medium transition-colors ${
                    selectedAddressId === null
                      ? "border-[var(--color-honey)] bg-[var(--color-honey)]/10 text-[var(--color-forest)]"
                      : "border-[var(--color-forest)]/20 text-[var(--color-ink)] opacity-60 hover:border-[var(--color-honey)]/50"
                  }`}
                >
                  + 새 배송지 입력
                </button>
              </div>
            )}

            <div className="flex flex-col gap-3">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <Field label="받는 분">
                  <input
                    type="text"
                    value={form.receiver}
                    onChange={updateField("receiver")}
                    readOnly={selectedAddressId !== null}
                    placeholder="이름"
                    className={`w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none ${selectedAddressId !== null ? "bg-[var(--color-forest)]/5 text-[var(--color-ink)]/70" : ""}`}
                  />
                </Field>
                <Field label="연락처">
                  <input
                    type="tel"
                    inputMode="numeric"
                    value={form.phone}
                    onChange={handlePhoneChange}
                    readOnly={selectedAddressId !== null}
                    placeholder="010-0000-0000"
                    maxLength={13}
                    className={`w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none ${selectedAddressId !== null ? "bg-[var(--color-forest)]/5 text-[var(--color-ink)]/70" : ""}`}
                  />
                </Field>
              </div>

              <Field label="우편번호">
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={form.zipcode}
                    readOnly
                    placeholder="우편번호 검색을 눌러주세요"
                    className="w-full rounded-xl border border-[var(--color-forest)]/20 bg-[var(--color-forest)]/5 px-3.5 py-2.5 text-sm text-[var(--color-ink)]/70 focus:outline-none"
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    shimmer={false}
                    onClick={handleSearchAddress}
                    disabled={selectedAddressId !== null}
                    className="shrink-0"
                  >
                    <Search size={15} className="mr-1" />
                    검색
                  </Button>
                </div>
              </Field>

              <Field label="주소">
                <input
                  type="text"
                  value={form.address}
                  readOnly
                  placeholder="우편번호 검색 시 자동 입력됩니다"
                  className="w-full rounded-xl border border-[var(--color-forest)]/20 bg-[var(--color-forest)]/5 px-3.5 py-2.5 text-sm text-[var(--color-ink)]/70 focus:outline-none"
                />
              </Field>

              <Field label="상세 주소">
                <input
                  ref={addressDetailRef}
                  type="text"
                  value={form.addressDetail}
                  onChange={updateField("addressDetail")}
                  readOnly={selectedAddressId !== null}
                  placeholder="동/호수 등 상세 주소"
                  className={`w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none ${selectedAddressId !== null ? "bg-[var(--color-forest)]/5 text-[var(--color-ink)]/70" : ""}`}
                />
              </Field>

              <Field label="배송 요청사항">
                <select
                  value={form.request}
                  onChange={updateField("request")}
                  className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
                >
                  <option>문 앞에 놔주세요</option>
                  <option>배송 전 연락해주세요</option>
                  <option>경비실에 맡겨주세요</option>
                  <option>직접 입력</option>
                </select>
              </Field>
            </div>
          </section>

          {/* 쿠폰 */}
          {coupons.length > 0 && (
            <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
              <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">쿠폰</h2>
              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                <button
                  type="button"
                  onClick={() => setSelectedCouponId(null)}
                  className={`flex flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed p-3 text-sm font-medium transition-colors ${
                    selectedCouponId === null
                      ? "border-[var(--color-honey)] bg-[var(--color-honey)]/10 text-[var(--color-forest)]"
                      : "border-[var(--color-forest)]/20 text-[var(--color-ink)] opacity-60 hover:border-[var(--color-honey)]/50"
                  }`}
                >
                  쿠폰 사용 안 함
                </button>
                {coupons.map((coupon) => {
                  const isChosen = selectedCouponId === coupon.memberCouponId;
                  const isEligible = subtotal >= coupon.minimumOrderAmount;
                  return (
                    <button
                      key={coupon.memberCouponId}
                      type="button"
                      disabled={!isEligible}
                      onClick={() => setSelectedCouponId(coupon.memberCouponId)}
                      className={`flex flex-col gap-1 rounded-xl border-2 p-3 text-left transition-colors ${
                        !isEligible
                          ? "cursor-not-allowed border-[var(--color-forest)]/10 bg-[var(--color-forest)]/5 opacity-40"
                          : isChosen
                            ? "border-[var(--color-honey)] bg-[var(--color-honey)]/10"
                            : "border-[var(--color-forest)]/15 bg-white hover:border-[var(--color-honey)]/50"
                      }`}
                    >
                      <span className="text-sm font-medium text-[var(--color-ink)]">
                        {coupon.couponName}
                      </span>
                      <span className="text-xs text-[var(--color-coral)]">
                        {coupon.discountAmount.toLocaleString()}원 할인
                      </span>
                      <span className="text-xs text-[var(--color-ink)] opacity-50">
                        {isEligible
                          ? `~${coupon.expiresAt?.slice(0, 10)}까지`
                          : `최소주문금액 ${coupon.minimumOrderAmount.toLocaleString()}원`}
                      </span>
                    </button>
                  );
                })}
              </div>
            </section>
          )}

          {/* 결제수단 */}
          <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
            <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">결제 수단</h2>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              {PAYMENT_METHODS.map((method) => {
                const isSelected = paymentMethod === method.id;
                const Icon = method.icon;
                return (
                  <motion.label
                    key={method.id}
                    htmlFor={`payment-${method.id}`}
                    animate={{
                      scale: isSelected ? 1.03 : 1,
                      borderColor: isSelected ? "var(--color-honey)" : "rgba(27,59,54,0.15)",
                    }}
                    transition={{ duration: 0.2, ease: "easeOut" }}
                    className={`relative flex cursor-pointer flex-col gap-2 rounded-2xl border-2 bg-white p-4 ${
                      isSelected ? "shadow-[0_10px_24px_rgba(242,169,59,0.25)]" : "shadow-sm"
                    }`}
                  >
                    <input
                      type="radio"
                      id={`payment-${method.id}`}
                      name="paymentMethod"
                      value={method.id}
                      checked={isSelected}
                      onChange={() => setPaymentMethod(method.id)}
                      className="sr-only"
                    />
                    <span
                      className={`flex h-9 w-9 items-center justify-center rounded-full ${
                        isSelected
                          ? "bg-[var(--color-honey)] text-[var(--color-forest)]"
                          : "bg-[var(--color-forest)]/8 text-[var(--color-forest)]/60"
                      }`}
                    >
                      <Icon size={17} />
                    </span>
                    <span className="flex items-center gap-1.5 text-sm font-medium text-[var(--color-ink)]">
                      {method.label}
                    </span>
                    <span className="text-sm text-[var(--color-ink)] opacity-60">
                      {method.description}
                    </span>
                  </motion.label>
                );
              })}
            </div>

            {paymentMethod === "VIRTUAL_CARD" && (
              <div className="mt-4 border-t border-[var(--color-forest)]/10 pt-4">
                <p className="mb-3 text-sm font-medium text-[var(--color-ink)] opacity-80">
                  결제할 카드 선택
                </p>
                {cards === null ? (
                  <div className="flex flex-col gap-2">
                    <Skeleton variant="rectangular" className="h-16 w-full" />
                    <Skeleton variant="rectangular" className="h-16 w-full" />
                  </div>
                ) : cards.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-[var(--color-forest)]/25 bg-[var(--color-forest)]/5 p-4 text-center">
                    <p className="text-sm text-[var(--color-ink)] opacity-70">
                      등록된 카드가 없습니다. 카드 관리에서 먼저 등록해주세요.
                    </p>
                    <Link
                      to="/cards"
                      className="mt-2 inline-block text-sm font-medium text-[var(--color-coral)] hover:underline"
                    >
                      카드 관리로 이동
                    </Link>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                    {cards.map((card) => {
                      const isSelectable = card.status === "ACTIVE";
                      const isChosen = selectedCardId === card.id;
                      return (
                        <button
                          key={card.id}
                          type="button"
                          disabled={!isSelectable}
                          onClick={() => setSelectedCardId(card.id)}
                          className={`flex flex-col gap-1 rounded-xl border-2 p-3 text-left transition-colors ${
                            !isSelectable
                              ? "cursor-not-allowed border-[var(--color-forest)]/10 bg-[var(--color-forest)]/5 opacity-40"
                              : isChosen
                                ? "border-[var(--color-honey)] bg-[var(--color-honey)]/10"
                                : "border-[var(--color-forest)]/15 bg-white hover:border-[var(--color-honey)]/50"
                          }`}
                        >
                          <span className="flex items-center justify-between gap-2">
                            <span className="font-mono text-sm font-medium text-[var(--color-ink)]">
                              {card.maskedNumber}
                            </span>
                            {!isSelectable && (
                              <span className="rounded-full bg-[var(--color-forest)]/10 px-2 py-0.5 text-[11px] font-medium text-[var(--color-ink)] opacity-70">
                                {CARD_STATUS_LABEL[card.status]}
                              </span>
                            )}
                          </span>
                          <span className="text-xs text-[var(--color-ink)] opacity-50">
                            가용 한도 {card.availableLimit.toLocaleString()}원
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </section>
        </div>

        {/* 최종 주문 내역 */}
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="rounded-2xl bg-white p-6 shadow-[0_10px_30px_rgba(27,59,54,0.10)]">
            <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">최종 주문 내역</h2>

            <ul className="flex flex-col gap-2.5 border-b border-[var(--color-forest)]/10 pb-4">
              {checkoutItems.map((item) => (
                <li key={item.id} className="flex items-center justify-between text-sm">
                  <span className="line-clamp-1 text-[var(--color-ink)] opacity-80">
                    {item.title} <span className="opacity-50">x{item.quantity}</span>
                  </span>
                  <span className="shrink-0 pl-2 text-[var(--color-ink)]">
                    {(item.price * item.quantity).toLocaleString()}원
                  </span>
                </li>
              ))}
            </ul>

            <dl className="mt-4 space-y-2.5 text-sm">
              <Row label="총 상품 금액" value={`${subtotal.toLocaleString()}원`} />
              <Row
                label="쿠폰 할인"
                value={couponDiscount > 0 ? `-${couponDiscount.toLocaleString()}원` : "-"}
                tone={couponDiscount > 0 ? "coral" : undefined}
              />
              <Row
                label="배송비"
                value={shippingFee > 0 ? `+${shippingFee.toLocaleString()}원` : "무료"}
              />
            </dl>

            <div className="mt-4 flex items-baseline justify-between border-t border-[var(--color-forest)]/10 pt-4">
              <span className="text-sm text-[var(--color-ink)]/70">최종 결제 금액</span>
              <span className="font-display text-2xl text-[var(--color-coral)]">
                {finalTotal.toLocaleString()}원
              </span>
            </div>

            {/* hover 시 사자 발자국이 살짝 지나가는 디테일 */}
            <div className="group relative mt-5">
              <Button variant="primary" size="lg" fullWidth onClick={validateAndOpenConfirm}>
                결제 진행하기
              </Button>
              <div className="pointer-events-none absolute inset-x-8 bottom-2 flex justify-between">
                {[0, 1, 2].map((i) => (
                  <PawPrint
                    key={i}
                    size={10}
                    className="text-[var(--color-forest)] opacity-0 transition-opacity duration-300 group-hover:opacity-30"
                    style={{ transitionDelay: `${i * 90}ms` }}
                  />
                ))}
              </div>
            </div>
          </div>
        </aside>
      </div>

      {/* 결제 최종 확인 모달 */}
      <Modal
        isOpen={isConfirmOpen}
        onClose={() => !isPlacingOrder && setIsConfirmOpen(false)}
        closeOnOverlayClick={!isPlacingOrder}
        title="결제를 진행할까요?"
        footer={
          <>
            <Button
              variant="ghost"
              disabled={isPlacingOrder}
              onClick={() => setIsConfirmOpen(false)}
            >
              취소
            </Button>
            <Button variant="primary" loading={isPlacingOrder} onClick={handleConfirmPayment}>
              결제 확정
            </Button>
          </>
        }
      >
        <p className="text-sm text-[var(--color-ink)] opacity-80">
          <span className="font-display text-base text-[var(--color-ink)]">
            {finalTotal.toLocaleString()}원
          </span>
          을 <strong>{selectedMethod?.label}</strong>(으)로 결제합니다.
          {form.address && (
            <>
              <br />
              배송지: {form.address} {form.addressDetail}
            </>
          )}
        </p>
      </Modal>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">{label}</span>
      {children}
    </label>
  );
}

function Row({ label, value, tone }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-[var(--color-ink)]/70">{label}</dt>
      <dd className={tone === "coral" ? "text-[var(--color-coral)]" : "text-[var(--color-ink)]"}>
        {value}
      </dd>
    </div>
  );
}

function CheckoutSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-[1fr_360px]">
      <div className="flex flex-col gap-6">
        <Skeleton variant="rectangular" className="h-96 w-full" />
        <Skeleton variant="rectangular" className="h-40 w-full" />
      </div>
      <Skeleton variant="rectangular" className="h-80 w-full" />
    </div>
  );
}
