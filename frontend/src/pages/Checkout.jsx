import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Search, Wallet, CreditCard, Landmark, PawPrint } from "lucide-react";
import Button from "../components/Button.jsx";
import Modal from "../components/Modal.jsx";
import Skeleton from "../components/Skeleton.jsx";
import { useToast } from "../components/Toast.jsx";
import { fetchCheckoutSummary } from "../api/checkout.js";
import { getMyCards } from "../api/cards.ts";

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
    id: "KAKAOPAY",
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
  {
    id: "BANK_TRANSFER",
    label: "무통장입금",
    description: "입금 확인 후 배송 시작",
    icon: Landmark,
  },
];

function loadDaumPostcodeScript() {
  return new Promise((resolve, reject) => {
    if (window.daum?.Postcode) return resolve();
    const script = document.createElement("script");
    script.src = "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

export default function Checkout() {
  const navigate = useNavigate();
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
  const [summary, setSummary] = useState(null);
  const [cards, setCards] = useState(null);

  useEffect(() => {
    let ignore = false;
    Promise.all([fetchCheckoutSummary(), getMyCards()]).then(([summaryData, cardsData]) => {
      if (ignore) return;
      setSummary(summaryData);
      setCards(cardsData);
    });
    return () => {
      ignore = true;
    };
  }, []);

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
    if (paymentMethod === "KAKAOPAY") {
      // TODO: 백엔드 결제 요청 API 붙으면 카카오페이 결제 URL로 리다이렉트 예정 (엔드포인트 확정 후 구현).
      // 지금은 콜백 페이지 흐름만 미리 확인할 수 있도록 success 콜백으로 바로 이동한다(실제 결제 없음, 데모용).
      navigate("/payment/kakao/success?pg_token=demo-pg-token");
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
      await loadDaumPostcodeScript();
      new window.daum.Postcode({
        oncomplete: (data) => {
          setForm((prev) => ({
            ...prev,
            zipcode: data.zonecode,
            address: data.roadAddress || data.jibunAddress,
          }));
          addressDetailRef.current?.focus();
        },
      }).open();
    } catch {
      toast.error("우편번호 검색을 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
    }
  };

  const handleConfirmPayment = () => {
    setIsConfirmOpen(false);
    toast.success("결제가 완료되었습니다.");
    // 주문완료 전용 페이지가 아직 없어서 임시로 마이페이지 주문내역 탭으로 이동
    setTimeout(() => navigate("/mypage?tab=orders"), 1500);
  };

  if (!summary) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <h1 className="font-display mb-6 text-2xl text-[var(--color-forest)]">주문 / 결제</h1>
        <CheckoutSkeleton />
      </div>
    );
  }

  const subtotal = summary.items.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const finalTotal = subtotal + summary.shippingFee - summary.couponDiscount;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <h1 className="font-display mb-6 text-2xl text-[var(--color-forest)]">주문 / 결제</h1>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[1fr_360px]">
        <div className="flex flex-col gap-6">
          {/* 배송지 입력 */}
          <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
            <h2 className="font-display mb-4 text-lg text-[var(--color-forest)]">배송지 정보</h2>
            <div className="flex flex-col gap-3">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <Field label="받는 분">
                  <input
                    type="text"
                    value={form.receiver}
                    onChange={updateField("receiver")}
                    placeholder="이름"
                    className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
                  />
                </Field>
                <Field label="연락처">
                  <input
                    type="tel"
                    inputMode="numeric"
                    value={form.phone}
                    onChange={handlePhoneChange}
                    placeholder="010-0000-0000"
                    maxLength={13}
                    className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
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
                  <Button type="button" variant="secondary" shimmer={false} onClick={handleSearchAddress} className="shrink-0">
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
                  placeholder="동/호수 등 상세 주소"
                  className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
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
                    <span className="text-sm font-medium text-[var(--color-ink)]">{method.label}</span>
                    <span className="text-sm text-[var(--color-ink)] opacity-60">{method.description}</span>
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
              {summary.items.map((item) => (
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
              <Row label="배송비" value={`+${summary.shippingFee.toLocaleString()}원`} />
              <Row label="쿠폰 할인" value={`-${summary.couponDiscount.toLocaleString()}원`} tone="coral" />
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
        onClose={() => setIsConfirmOpen(false)}
        title="결제를 진행할까요?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setIsConfirmOpen(false)}>
              취소
            </Button>
            <Button variant="primary" onClick={handleConfirmPayment}>
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
      <dd className={tone === "coral" ? "text-[var(--color-coral)]" : "text-[var(--color-ink)]"}>{value}</dd>
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
