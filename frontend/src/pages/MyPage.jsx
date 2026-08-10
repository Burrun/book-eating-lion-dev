import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { DndContext, useDraggable, useDroppable } from "@dnd-kit/core";
import { AnimatePresence, motion } from "framer-motion";
import { Award, BookOpen, Flame, Quote, Send, Star } from "lucide-react";
import Button from "../components/Button.jsx";
import Modal from "../components/Modal.jsx";
import Skeleton from "../components/Skeleton.jsx";
import { useToast } from "../components/Toast.jsx";
import {
  fetchProfile,
  fetchFedBooks,
  fetchReadingNotes,
  askLion,
  fetchOrders,
  fetchCoupons,
  fetchReturnRequests,
  fetchRestockRequests,
  fetchReviews,
} from "../api/mypage.js";

const BADGE_ICONS = { achievement: Award, reading: BookOpen, streak: Flame };
const EXP_PER_BOOK = 15;

const ORDER_TABS = [
  { id: "orders", label: "주문/배송 조회" },
  { id: "coupons", label: "쿠폰 현황" },
  { id: "returns", label: "취소/교환/반품/환불" },
  { id: "restock", label: "재입고 알림 신청" },
  { id: "cards", label: "카드 관리" },
];

const ORDER_STATUS_META = {
  shipping: { label: "배송중", className: "bg-[var(--color-honey)]/20 text-[var(--color-forest)]" },
  delivered: { label: "배송완료", className: "bg-[var(--color-forest)]/10 text-[var(--color-forest)]" },
  canceled: { label: "주문취소", className: "bg-[var(--color-coral)]/10 text-[var(--color-coral)]" },
};

const RETURN_STATUS_META = {
  requested: { label: "접수완료", className: "bg-[var(--color-forest)]/10 text-[var(--color-forest)]" },
  processing: { label: "처리중", className: "bg-[var(--color-honey)]/20 text-[var(--color-forest)]" },
  completed: { label: "환불완료", className: "bg-[var(--color-coral)]/10 text-[var(--color-coral)]" },
};

export default function MyPage() {
  const [profile, setProfile] = useState(null);
  const [level, setLevel] = useState(0);
  const [exp, setExp] = useState(0);

  useEffect(() => {
    let ignore = false;
    fetchProfile().then((data) => {
      if (ignore) return;
      setProfile(data);
      setLevel(data.level);
      setExp(data.exp);
    });
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      {profile ? (
        <ProfileCard profile={profile} level={level} />
      ) : (
        <Skeleton variant="rectangular" className="h-28 w-full" />
      )}

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <LionFeedingCard exp={exp} setExp={setExp} setLevel={setLevel} />
        <LionRagCard />
      </div>

      <OrdersSection />
      <ReviewsSection />
    </div>
  );
}

function ProfileCard({ profile, level }) {
  return (
    <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <div className="flex items-center gap-4">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-[var(--color-honey)]/20 text-3xl">
          🦁
        </div>
        <h1 className="font-display text-xl text-[var(--color-forest)]">
          {profile.name} 님의 마이페이지{" "}
          <span className="text-[var(--color-honey)]">
            (Lv.{level} {profile.title})
          </span>
        </h1>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        {profile.badges.map((badge) => {
          const Icon = BADGE_ICONS[badge.type] ?? Award;
          return (
            <span
              key={badge.label}
              className="flex items-center gap-1.5 rounded-full bg-[var(--color-forest)]/5 px-3 py-1.5 text-sm text-[var(--color-forest)]"
            >
              <Icon size={14} />
              {badge.label}
            </span>
          );
        })}
      </div>
    </section>
  );
}

function LionFeedingCard({ exp, setExp, setLevel }) {
  const [books, setBooks] = useState(null);
  const [isFeeding, setIsFeeding] = useState(false);

  useEffect(() => {
    let ignore = false;
    fetchFedBooks().then((data) => {
      if (!ignore) setBooks(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  const handleDragEnd = (event) => {
    const { active, over } = event;
    if (over?.id !== "lion-mouth") return;
    const book = books?.find((b) => b.id === active.id);
    if (!book) return;

    setBooks((prev) => prev.filter((b) => b.id !== active.id));
    setIsFeeding(true);
    setExp((prev) => {
      const next = prev + EXP_PER_BOOK;
      if (next >= 100) {
        setLevel((lv) => lv + 1);
        return next - 100;
      }
      return next;
    });
    setTimeout(() => setIsFeeding(false), 600);
  };

  return (
    <section className="rounded-2xl border-2 border-[var(--color-honey)]/40 bg-[var(--color-honey)]/5 p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <h2 className="font-display mb-5 text-lg text-[var(--color-forest)]">
        사자 성장 & 완독 도서 먹이기
      </h2>

      <DndContext onDragEnd={handleDragEnd}>
        <div className="flex flex-col items-center gap-6">
          <LionDropZone exp={exp} isFeeding={isFeeding} />

          <div className="grid w-full grid-cols-2 gap-3">
            {books === null ? (
              <>
                <Skeleton variant="rectangular" className="h-24 w-full" />
                <Skeleton variant="rectangular" className="h-24 w-full" />
              </>
            ) : books.length === 0 ? (
              <p className="col-span-2 py-4 text-center text-sm text-[var(--color-ink)] opacity-50">
                완독한 책을 모두 사자에게 먹였어요! 🦁
              </p>
            ) : (
              books.map((book) => <DraggableBook key={book.id} id={book.id} title={book.title} />)
            )}
          </div>
        </div>
      </DndContext>

      <p className="mt-5 flex items-center justify-center gap-1.5 text-sm text-[var(--color-ink)] opacity-50">
        🐾 사자 입으로 드래그하여 먹이기
      </p>
    </section>
  );
}

function LionDropZone({ exp, isFeeding }) {
  const { setNodeRef, isOver } = useDroppable({ id: "lion-mouth" });

  return (
    <div className="flex w-full flex-col items-center gap-4">
      <div
        ref={setNodeRef}
        className={`relative flex h-32 w-32 items-center justify-center rounded-full border-4 transition-all duration-200 ${
          isOver
            ? "scale-110 border-[var(--color-honey)] bg-[var(--color-honey)]/25"
            : "border-[var(--color-honey)]/40 bg-[var(--color-honey)]/10"
        }`}
      >
        <motion.span
          animate={isFeeding ? { scale: [1, 1.3, 1], rotate: [0, -8, 8, 0] } : {}}
          transition={{ duration: 0.5 }}
          className="text-5xl leading-none"
        >
          🦁
        </motion.span>
        <AnimatePresence>
          {isFeeding && (
            <motion.span
              initial={{ opacity: 0, y: 0, scale: 0.8 }}
              animate={{ opacity: 1, y: -36, scale: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.6, ease: "easeOut" }}
              className="font-display pointer-events-none absolute top-0 text-sm text-[var(--color-honey)]"
            >
              +{EXP_PER_BOOK} EXP
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      <div className="w-full">
        <div className="h-3 w-full overflow-hidden rounded-full bg-[var(--color-forest)]/10">
          <motion.div
            className="h-full rounded-full bg-[var(--color-honey)]"
            animate={{ width: `${exp}%` }}
            transition={{ duration: 0.5, ease: "easeOut" }}
          />
        </div>
        <p className="mt-1.5 text-center text-sm text-[var(--color-ink)] opacity-70">
          EXP: {exp} / 100 <span className="opacity-70">(다음 레벨까지 {100 - exp})</span>
        </p>
      </div>
    </div>
  );
}

function DraggableBook({ id, title }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id });
  const style = transform
    ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` }
    : undefined;

  return (
    <button
      type="button"
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      className={`flex cursor-grab flex-col items-center justify-center gap-1.5 rounded-2xl border-2 border-dashed border-[var(--color-forest)]/30 bg-white px-4 py-5 text-center shadow-sm transition-shadow active:cursor-grabbing ${
        isDragging ? "z-50 shadow-lg opacity-90" : ""
      }`}
    >
      <BookOpen size={20} className="text-[var(--color-forest)]/50" />
      <span className="text-sm font-medium text-[var(--color-ink)]">{title}</span>
      <span className="text-[11px] text-[var(--color-ink)] opacity-40">Drag me</span>
    </button>
  );
}

function LionRagCard() {
  const [notes, setNotes] = useState(null);
  const [question, setQuestion] = useState("");
  const [status, setStatus] = useState("idle"); // idle | loading | streaming | done
  const [displayedAnswer, setDisplayedAnswer] = useState("");
  const [similarity, setSimilarity] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetchReadingNotes().then((data) => {
      if (!ignore) setNotes(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  const handleAsk = async () => {
    if (!question.trim() || status === "loading" || status === "streaming") return;
    setStatus("loading");
    setDisplayedAnswer("");

    const answer = await askLion(question);
    setSimilarity(answer.similarity);
    setStatus("streaming");

    const full = answer.text;
    let i = 0;
    const interval = setInterval(() => {
      i += 2;
      setDisplayedAnswer(full.slice(0, i));
      if (i >= full.length) {
        clearInterval(interval);
        setStatus("done");
      }
    }, 25);
  };

  const isBusy = status === "loading" || status === "streaming";

  return (
    <section className="rounded-2xl border-2 border-[var(--color-forest)]/15 bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <h2 className="font-display mb-4 flex items-center gap-2 text-lg text-[var(--color-forest)]">
        🦁 사자에게 물어보기{" "}
        <span className="text-sm font-normal text-[var(--color-ink)] opacity-40">(Spring AI RAG)</span>
      </h2>

      <div className="mb-4">
        <p className="mb-2 text-sm font-medium text-[var(--color-ink)] opacity-70">
          내 독서 메모 & 인용구 저장소
        </p>
        {notes === null ? (
          <div className="flex flex-col gap-1.5">
            <Skeleton variant="text" className="w-full" />
            <Skeleton variant="text" className="w-2/3" />
          </div>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {notes.map((note) => (
              <li
                key={note.id}
                className="flex items-start gap-2 rounded-xl bg-[var(--color-forest)]/5 px-3 py-2 text-sm"
              >
                <Quote size={13} className="mt-0.5 shrink-0 text-[var(--color-forest)]/40" />
                <span className="text-[var(--color-ink)]">
                  <span className="font-medium text-[var(--color-forest)]">[{note.book}]</span> {note.quote}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleAsk();
        }}
        className="flex gap-2"
      >
        <input
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="내가 작성한 메모 중 객체지향 관련 있어?"
          className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
        <Button type="submit" variant="primary" shimmer={false} disabled={isBusy} className="shrink-0 px-4">
          <Send size={15} />
        </Button>
      </form>

      {status !== "idle" && (
        <div className="mt-4 rounded-xl bg-[var(--color-forest)]/5 p-4">
          <div className="flex items-start gap-2.5">
            <span className="text-lg leading-none">🦁</span>
            {status === "loading" ? (
              <span className="flex items-center gap-1 pt-1.5">
                {[0, 1, 2].map((i) => (
                  <motion.span
                    key={i}
                    className="h-1.5 w-1.5 rounded-full bg-[var(--color-forest)]/40"
                    animate={{ opacity: [0.3, 1, 0.3] }}
                    transition={{ duration: 1, repeat: Infinity, delay: i * 0.15 }}
                  />
                ))}
              </span>
            ) : (
              <p className="text-sm text-[var(--color-ink)]">
                <span className="font-medium text-[var(--color-forest)]">사자 사서 답변: </span>
                {displayedAnswer}
                {status === "streaming" && <span className="animate-pulse">▌</span>}
                {status === "done" && (
                  <span className="ml-2 inline-block rounded-full bg-[var(--color-honey)]/20 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
                    유사도 {similarity}%
                  </span>
                )}
              </p>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

function OrdersSection() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab");
  const activeTab = ORDER_TABS.some((t) => t.id === requestedTab) ? requestedTab : "orders";

  // 카드 관리는 이 안에 임베드하지 않고 기존 /cards 라우트를 그대로 쓴다
  // (CardsPage는 자체 <main>과 섹션 레이아웃을 가진 독립 페이지라 탭 패널에 끼워 넣기엔 구조가 맞지 않는다).
  useEffect(() => {
    if (activeTab === "cards") {
      navigate("/cards", { replace: true });
    }
  }, [activeTab, navigate]);

  const setTab = (id) => {
    if (id === "cards") {
      navigate("/cards");
      return;
    }
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("tab", id);
      return next;
    });
  };

  return (
    <section className="mt-6 rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <h2 className="font-display mb-1 text-lg text-[var(--color-forest)]">주문 · 결제 관리</h2>

      <div className="mt-3 flex flex-wrap gap-1 border-b border-[var(--color-forest)]/10">
        {ORDER_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setTab(tab.id)}
            className={`border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
              activeTab === tab.id
                ? "border-[var(--color-honey)] text-[var(--color-forest)]"
                : "border-transparent text-[var(--color-ink)] opacity-50 hover:opacity-80"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="pt-5">
        {activeTab === "orders" && <OrdersTab />}
        {activeTab === "coupons" && <CouponsTab />}
        {activeTab === "returns" && <ReturnsTab />}
        {activeTab === "restock" && <RestockTab />}
      </div>
    </section>
  );
}

function TabSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      {Array.from({ length: 2 }).map((_, i) => (
        <Skeleton key={i} variant="rectangular" className="h-20 w-full" />
      ))}
    </div>
  );
}

function OrdersTab() {
  const [orders, setOrders] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetchOrders().then((data) => {
      if (!ignore) setOrders(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  if (!orders) return <TabSkeleton />;

  return (
    <div className="flex flex-col gap-3">
      {orders.map((order) => {
        const meta = ORDER_STATUS_META[order.status];
        return (
          <div
            key={order.id}
            className="flex flex-col gap-3 rounded-xl border border-[var(--color-forest)]/10 p-4 sm:flex-row sm:items-center sm:justify-between"
          >
            <div>
              <p className="text-sm text-[var(--color-ink)] opacity-50">
                {order.orderNo} · {order.date}
              </p>
              <p className="mt-0.5 text-sm font-medium text-[var(--color-ink)]">{order.title}</p>
              <p className="font-display mt-1 text-base text-[var(--color-ink)]">
                {order.price.toLocaleString()}원
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${meta.className}`}>
                {meta.label}
              </span>
              {order.status === "shipping" && (
                <>
                  <Button variant="secondary" size="sm" shimmer={false}>
                    배송조회
                  </Button>
                  <Button variant="ghost" size="sm" shimmer={false} className="text-[var(--color-coral)]">
                    취소/반품
                  </Button>
                </>
              )}
              {order.status === "delivered" && (
                <>
                  <Button variant="primary" size="sm" shimmer={false}>
                    구매확정
                  </Button>
                  <Button variant="secondary" size="sm" shimmer={false}>
                    리뷰작성
                  </Button>
                </>
              )}
              {order.status === "canceled" && (
                <Button variant="ghost" size="sm" shimmer={false}>
                  상세보기
                </Button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function CouponsTab() {
  const [state, setState] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetchCoupons().then((data) => {
      if (!ignore) setState(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  if (!state) return <TabSkeleton />;

  return (
    <div className="flex flex-col gap-5">
      <ul className="flex flex-col gap-2">
        {state.coupons.map((coupon) => (
          <li
            key={coupon.id}
            className={`flex items-center justify-between rounded-xl border border-dashed px-4 py-3 ${
              coupon.status === "expired" ? "border-[var(--color-ink)]/15 opacity-40" : "border-[var(--color-honey)]/50"
            }`}
          >
            <div>
              <p className="text-sm font-medium text-[var(--color-ink)]">{coupon.label}</p>
              <p className="text-sm text-[var(--color-ink)] opacity-50">~{coupon.expiresAt}까지</p>
            </div>
            <span
              className={`text-xs font-medium ${
                coupon.status === "expired" ? "text-[var(--color-ink)] opacity-40" : "text-[var(--color-coral)]"
              }`}
            >
              {coupon.status === "expired" ? "기간 만료" : "사용 가능"}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ReturnsTab() {
  const [requests, setRequests] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetchReturnRequests().then((data) => {
      if (!ignore) setRequests(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  if (!requests) return <TabSkeleton />;
  if (requests.length === 0) {
    return <EmptyState message="진행 중인 취소/교환/반품 내역이 없어요" />;
  }

  return (
    <ul className="flex flex-col gap-3">
      {requests.map((request) => {
        const meta = RETURN_STATUS_META[request.status];
        return (
          <li
            key={request.id}
            className="flex items-center justify-between rounded-xl border border-[var(--color-forest)]/10 p-4"
          >
            <div>
              <p className="text-sm text-[var(--color-ink)] opacity-50">{request.orderNo}</p>
              <p className="mt-0.5 text-sm font-medium text-[var(--color-ink)]">{request.title}</p>
              <p className="text-sm text-[var(--color-ink)] opacity-50">사유: {request.reason}</p>
            </div>
            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${meta.className}`}>
              {meta.label}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

function RestockTab() {
  const [requests, setRequests] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetchRestockRequests().then((data) => {
      if (!ignore) setRequests(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  if (!requests) return <TabSkeleton />;
  if (requests.length === 0) {
    return <EmptyState message="신청한 재입고 알림이 없어요" />;
  }

  return (
    <ul className="flex flex-col gap-3">
      {requests.map((item) => (
        <li
          key={item.id}
          className="flex items-center justify-between rounded-xl border border-[var(--color-forest)]/10 p-4"
        >
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-[var(--color-forest)]/5 text-[var(--color-forest)] opacity-40">
              <BookOpen size={18} />
            </span>
            <div>
              <p className="text-sm font-medium text-[var(--color-ink)]">{item.title}</p>
              <p className="text-sm text-[var(--color-ink)] opacity-50">{item.requestedAt} 신청</p>
            </div>
          </div>
          <Button
            variant="ghost"
            size="sm"
            shimmer={false}
            onClick={() => setRequests((prev) => prev.filter((r) => r.id !== item.id))}
          >
            신청 취소
          </Button>
        </li>
      ))}
    </ul>
  );
}

function EmptyState({ message }) {
  return <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-40">{message}</p>;
}

function ReviewsSection() {
  const toast = useToast();
  const [reviews, setReviews] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [editingReview, setEditingReview] = useState(null);
  const [editForm, setEditForm] = useState({ rating: 5, content: "" });

  useEffect(() => {
    let ignore = false;
    fetchReviews().then((data) => {
      if (!ignore) setReviews(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  const handleDelete = () => {
    setReviews((prev) => prev.filter((r) => r.id !== pendingDelete.id));
    setPendingDelete(null);
    toast.success("리뷰가 삭제되었습니다.");
  };

  const openEdit = (review) => {
    setEditingReview(review);
    setEditForm({ rating: review.rating, content: review.content });
  };

  const handleSaveEdit = () => {
    if (!editForm.content.trim()) {
      toast.error("리뷰 내용을 입력해주세요.");
      return;
    }
    setReviews((prev) =>
      prev.map((r) =>
        r.id === editingReview.id ? { ...r, rating: editForm.rating, content: editForm.content.trim() } : r
      )
    );
    setEditingReview(null);
    toast.success("리뷰가 수정되었습니다.");
  };

  return (
    <section className="mt-6 rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <h2 className="font-display mb-5 text-lg text-[var(--color-forest)]">
        내가 작성한 한줄평 & 도서 리뷰 관리
      </h2>

      {reviews === null ? (
        <TabSkeleton />
      ) : reviews.length === 0 ? (
        <EmptyState message="아직 작성한 리뷰가 없어요" />
      ) : (
        <ul className="flex flex-col gap-3">
          {reviews.map((review) => (
            <li
              key={review.id}
              className="flex gap-4 rounded-xl border border-[var(--color-forest)]/10 p-4"
            >
              <div className="flex h-20 w-14 shrink-0 items-center justify-center rounded-lg bg-[var(--color-forest)]/5">
                <BookOpen size={20} className="text-[var(--color-forest)] opacity-25" />
              </div>
              <div className="flex flex-1 flex-col gap-1.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-sm font-medium text-[var(--color-ink)]">{review.book}</p>
                    <div className="mt-1 flex items-center gap-1.5">
                      <StarRating rating={review.rating} />
                      <span className="text-sm text-[var(--color-ink)] opacity-50">
                        ({review.rating.toFixed(1)}) · {review.date}
                      </span>
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-1.5">
                    <Button variant="secondary" size="sm" shimmer={false} onClick={() => openEdit(review)}>
                      수정
                    </Button>
                    <Button variant="coral" size="sm" shimmer={false} onClick={() => setPendingDelete(review)}>
                      삭제
                    </Button>
                  </div>
                </div>
                <p className="text-sm text-[var(--color-ink)] opacity-80">{review.content}</p>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal
        isOpen={!!pendingDelete}
        onClose={() => setPendingDelete(null)}
        title="리뷰를 삭제할까요?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setPendingDelete(null)}>
              취소
            </Button>
            <Button variant="coral" onClick={handleDelete}>
              삭제
            </Button>
          </>
        }
      >
        <p className="text-sm text-[var(--color-ink)] opacity-80">
          "{pendingDelete?.book}" 리뷰를 삭제하면 되돌릴 수 없어요.
        </p>
      </Modal>

      <Modal
        isOpen={!!editingReview}
        onClose={() => setEditingReview(null)}
        title="리뷰 수정"
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditingReview(null)}>
              취소
            </Button>
            <Button variant="primary" onClick={handleSaveEdit}>
              저장
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <div>
            <p className="mb-1.5 text-sm font-medium text-[var(--color-ink)] opacity-80">{editingReview?.book}</p>
            <StarPicker
              rating={editForm.rating}
              onChange={(rating) => setEditForm((prev) => ({ ...prev, rating }))}
            />
          </div>
          <textarea
            value={editForm.content}
            onChange={(e) => setEditForm((prev) => ({ ...prev, content: e.target.value }))}
            rows={4}
            placeholder="이 책에 대한 감상을 남겨주세요"
            className="w-full resize-none rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </div>
      </Modal>
    </section>
  );
}

function StarRating({ rating }) {
  return (
    <div className="flex items-center gap-0.5 text-[var(--color-honey)]">
      {Array.from({ length: 5 }).map((_, i) => (
        <Star
          key={i}
          size={14}
          fill={i < Math.round(rating) ? "var(--color-honey)" : "none"}
          strokeWidth={1.5}
        />
      ))}
    </div>
  );
}

function StarPicker({ rating, onChange }) {
  return (
    <div className="flex items-center gap-1 text-[var(--color-honey)]">
      {Array.from({ length: 5 }).map((_, i) => (
        <button
          key={i}
          type="button"
          aria-label={`${i + 1}점`}
          onClick={() => onChange(i + 1)}
          className="p-0.5 transition-transform hover:scale-110"
        >
          <Star size={22} fill={i < rating ? "var(--color-honey)" : "none"} strokeWidth={1.5} />
        </button>
      ))}
    </div>
  );
}
