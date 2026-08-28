import { useCallback, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { DndContext, useDraggable, useDroppable } from "@dnd-kit/core";
import { AnimatePresence, motion } from "framer-motion";
import { Award, BookOpen, Flame, Send, Star, X } from "lucide-react";
import Button from "../components/Button.jsx";
import Modal from "../components/Modal.jsx";
import Skeleton from "../components/Skeleton.jsx";
import { useToast } from "../components/Toast.jsx";
import LionCharacter, { getLionTier } from "../components/LionCharacter.jsx";
import {
  fetchProfile,
  feedLion,
  askLion,
  fetchCoupons,
  fetchReturnRequests,
  fetchReviews,
  fetchLionStatus,
} from "../api/mypage.js";
import { getFeedableBooks, markBookFed } from "../api/readingProgress.ts";
import { getRecentBooks, getWishlist } from "../api/wishlist.ts";
import { deleteReview, updateReview } from "../api/reviews.ts";
import { cancelRestockAlert, getMyRestockAlerts } from "../api/restockAlerts.ts";
import { cancelSubscription, getMySubscription } from "../api/member.ts";
import { subscriptionCheckoutItem } from "../constants/subscription.ts";
import {
  cancelOrder,
  getMyOrders,
  getOrder,
  getOrderDelivery,
  requestOrderReturn,
} from "../api/orders.js";
// badges/streakCount는 member(GET /api/members/me)도 ai(GET /api/ai/lion/me)도 갖고 있지
// 않다 — 대응 API가 아직 없는 mock 전용 데이터라(mocks/mypage.js 참고) api 레이어를 거치지
// 않고 여기서 직접 가져와 mock 모드일 때만 profile에 붙인다.
import { MOCK_BADGES, MOCK_STREAK_COUNT } from "../mocks/mypage.js";

const BADGE_ICONS = { achievement: Award, reading: BookOpen, streak: Flame };

// 프로필(GET /api/members/me), 사자 성장/먹이기(GET·POST /api/ai/lion/**), RAG 물어보기
// (POST /api/ai/lion/ask), 주문목록, 쿠폰현황, 재입고 알림, 내가 작성한 리뷰(GET·PATCH·DELETE
// /api/catalog/reviews/**)는 실API가 있어 mock 여부와 무관하게 항상 노출한다. 반품 목록
// 조회만 여전히 백엔드 미구현(조회 엔드포인트 없음)이라 존재하지 않는 엔드포인트를 실서버
// 모드에서 호출하지 않도록 mock 모드에서만 노출한다.
//
// 사자에게 먹이는 대상은 "완독한 책"이다(GET /api/catalog/members/me/books/feedable).
// 예전에는 "완독 요약 메모"를 먹였고 그 텍스트가 RAG 근거로도 쓰였지만, 지금 메모는 EPUB
// 뷰어에서 문장 단위로 남기는 개인 기록이고 검색 근거로는 쓰이지 않는다 — 사자는 책 본문만
// 인용한다.
//
// 이북 보관함과 내 메모는 이 페이지가 아니라 EbookLibraryPage(/mypage/library) 에 있다.
// 마이페이지는 "계정/주문", 보관함은 "읽기"라 한 화면에 두면 스크롤만 길어졌다.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// 재입고 알림 신청 목록은 여기 탭으로 따로 두지 않는다 — "내 도서 활동"
// (BookActivitySection)의 재입고 알림 탭이 같은 API(GET /catalog/restock-alerts/me)로
// 이미 신청 취소까지 정상 동작해서, 이 탭은 중복이자 죽은 사본이었다(취소 버튼이 서버
// 호출 없이 화면에서만 지우는 가짜 구현이었다). 하나로 통일했다.
const ORDER_TABS = [
  { id: "orders", label: "주문/배송 조회" },
  { id: "coupons", label: "쿠폰 현황" },
  { id: "returns", label: "취소/교환/반품/환불" },
  { id: "subscription", label: "구독 관리" },
  { id: "cards", label: "카드 관리" },
  { id: "addresses", label: "배송지 관리" },
];

// orderStatus(결제/주문 생명주기)와 deliveryStatus(배송 진행)는 별개 리소스다 — 백엔드가
// Order/Delivery 두 엔티티로 분리해뒀다. 주문 목록(GET /api/orders)엔 orderStatus만 오고,
// deliveryStatus는 "배송조회" 클릭 시 그 주문 하나만 따로 조회한다(N+1 방지).
const ORDER_STATUS_META = {
  PENDING_PAYMENT: {
    label: "결제 대기",
    className: "bg-[var(--color-forest)]/10 text-[var(--color-forest)]",
  },
  PAID: { label: "결제 완료", className: "bg-[var(--color-honey)]/20 text-[var(--color-forest)]" },
  CANCELLED: {
    label: "주문취소",
    className: "bg-[var(--color-coral)]/10 text-[var(--color-coral)]",
  },
  RETURN_REQUESTED: {
    label: "반품/교환 접수됨",
    className: "bg-[var(--color-coral)]/10 text-[var(--color-coral)]",
  },
  REFUNDED: {
    label: "환불 완료",
    className: "bg-[var(--color-forest)]/10 text-[var(--color-forest)]",
  },
};

const DELIVERY_STATUS_LABEL = {
  PENDING: "배송 대기",
  SHIPPED: "배송 시작",
  IN_TRANSIT: "배송 중",
  DELIVERED: "배송 완료",
};

const RETURN_STATUS_META = {
  requested: {
    label: "접수완료",
    className: "bg-[var(--color-forest)]/10 text-[var(--color-forest)]",
  },
  processing: {
    label: "처리중",
    className: "bg-[var(--color-honey)]/20 text-[var(--color-forest)]",
  },
  completed: {
    label: "환불완료",
    className: "bg-[var(--color-coral)]/10 text-[var(--color-coral)]",
  },
};

export default function MyPage() {
  const { logout } = useAuth();
  const [profile, setProfile] = useState(null);
  const [profileError, setProfileError] = useState(false);
  const [level, setLevel] = useState(0);
  // 누적 경험치다(레벨업해도 차감되지 않는다). 진행바는 exp % 100 을 쓴다.
  const [exp, setExp] = useState(0);

  // 회원 정보와 사자 상태는 소유 서비스가 다르다 — member 와 ai.
  // 한 응답에 섞여 오지 않으므로 따로 부르고 화면에서 합친다.
  useEffect(() => {
    let ignore = false;
    fetchProfile()
      .then((data) => {
        if (ignore) return;
        // 프로필(member)과 배지(mock 전용, 대응 API 없음)는 소유처가 달라 따로 부르고
        // 컴포넌트에서 합친다. 실API 모드에서는 badges/streakCount를 붙이지 않는다 —
        // ProfileCard도 {USE_MOCK && ...}로 감싸져 있어 실모드에서는 렌더링되지 않는다.
        setProfile(
          USE_MOCK ? { ...data, badges: MOCK_BADGES, streakCount: MOCK_STREAK_COUNT } : data,
        );
      })
      .catch((err) => {
        if (ignore) return;
        // 액세스 토큰 만료(401)면 무한 스켈레톤 대신 로그아웃시켜 로그인 화면으로
        // 보낸다 — ProtectedRoute가 isAuthenticated 변화를 감지해 리다이렉트한다.
        if (err?.response?.status === 401) {
          logout();
          return;
        }
        setProfileError(true);
      });
    fetchLionStatus()
      .then((lion) => {
        if (ignore) return;
        setLevel(lion.level);
        setExp(lion.exp);
      })
      .catch(() => {
        // 사자 상태는 부가 정보라 실패해도 프로필 카드 자체는 보여준다.
      });
    return () => {
      ignore = true;
    };
  }, [logout]);

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      {profile ? (
        <ProfileCard profile={profile} level={level} />
      ) : profileError ? (
        <EmptyState message="프로필을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요." />
      ) : (
        <Skeleton variant="rectangular" className="h-28 w-full" />
      )}

      <div className="mt-6">
        <LionFeedingCard exp={exp} setExp={setExp} level={level} setLevel={setLevel} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <LionRagCard />
        <OrdersSection />
      </div>

      <BookActivitySection />

      <ReviewsSection />
    </div>
  );
}

const BOOK_ACTIVITY_TABS = [
  { id: "recent", label: "최근 본 도서" },
  { id: "restock", label: "재입고 알림" },
  { id: "wishlist", label: "찜 목록" },
];

const RESTOCK_STATUS_LABELS = {
  WAITING: "알림 대기",
  SENT: "알림 완료",
  FAILED: "발송 재시도",
  CANCELLED: "신청 취소",
};

function BookActivitySection() {
  const toast = useToast();
  const [activeTab, setActiveTab] = useState("recent");
  const [items, setItems] = useState(null);
  const [error, setError] = useState(null);
  const [cancellingBookId, setCancellingBookId] = useState(null);

  useEffect(() => {
    let ignore = false;

    const request =
      activeTab === "recent"
        ? getRecentBooks()
        : activeTab === "restock"
          ? getMyRestockAlerts()
          : getWishlist();

    request
      .then((data) => {
        if (!ignore) setItems(data);
      })
      .catch(() => {
        if (!ignore) {
          setItems([]);
          setError("도서 활동을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
      });

    return () => {
      ignore = true;
    };
  }, [activeTab]);

  const handleTabChange = (tabId) => {
    if (tabId === activeTab) return;
    setItems(null);
    setError(null);
    setActiveTab(tabId);
  };

  const handleCancelRestockAlert = async (bookId) => {
    setCancellingBookId(bookId);
    try {
      await cancelRestockAlert(bookId);
      setItems((prev) => prev.filter((item) => item.bookId !== bookId));
      toast.success("재입고 알림 신청이 취소되었습니다.");
    } catch {
      toast.error("재입고 알림 신청을 취소하지 못했습니다.");
    } finally {
      setCancellingBookId(null);
    }
  };

  return (
    <section className="mt-6 rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <h2 className="font-display mb-1 text-lg text-[var(--color-forest)]">내 도서 활동</h2>

      <div className="mt-3 flex flex-wrap gap-1 border-b border-[var(--color-forest)]/10">
        {BOOK_ACTIVITY_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => handleTabChange(tab.id)}
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
        {items === null ? (
          <TabSkeleton />
        ) : error ? (
          <EmptyState message={error} />
        ) : items.length === 0 ? (
          <EmptyState
            message={
              activeTab === "recent"
                ? "최근 본 도서가 없어요"
                : activeTab === "restock"
                  ? "신청한 재입고 알림이 없어요"
                  : "찜한 도서가 없어요"
            }
          />
        ) : activeTab === "restock" ? (
          <ul className="flex flex-col gap-3">
            {items.map((item) => (
              <li
                key={item.restockAlertId}
                className="flex items-center justify-between rounded-xl border border-[var(--color-forest)]/10 p-4"
              >
                <BookActivityTitle
                  title={item.title}
                  detail={`${item.requestedAt.slice(0, 10)} 신청`}
                />
                <div className="flex items-center gap-2">
                  <span className="rounded-full bg-[var(--color-honey)]/20 px-2.5 py-1 text-xs font-medium text-[var(--color-forest)]">
                    {RESTOCK_STATUS_LABELS[item.status] ?? item.status}
                  </span>
                  {item.status !== "CANCELLED" && (
                    <Button
                      variant="ghost"
                      size="sm"
                      shimmer={false}
                      disabled={cancellingBookId === item.bookId}
                      onClick={() => handleCancelRestockAlert(item.bookId)}
                    >
                      신청 취소
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {items.map((item) => (
              <li key={item.id} className="rounded-xl border border-[var(--color-forest)]/10 p-4">
                <BookActivityTitle
                  title={item.title}
                  detail={item.category ?? "도서"}
                  coverImageUrl={item.coverImageUrl}
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function BookActivityTitle({ title, detail, coverImageUrl }) {
  return (
    <div className="flex items-center gap-3">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[var(--color-forest)]/5 text-[var(--color-forest)]">
        {coverImageUrl ? (
          <img src={coverImageUrl} alt={title} className="h-full w-full object-cover" />
        ) : (
          <BookOpen size={18} className="opacity-40" />
        )}
      </span>
      <div>
        <p className="text-sm font-medium text-[var(--color-ink)]">{title}</p>
        <p className="text-sm text-[var(--color-ink)] opacity-50">{detail}</p>
      </div>
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
          {profile.name || profile.email || "회원"} 님의 마이페이지{" "}
          {USE_MOCK && (
            <span className="text-[var(--color-honey)]">
              (Lv.{level} {getLionTier(level).label})
            </span>
          )}
        </h1>
      </div>
      {USE_MOCK && (
        <div className="mt-4 flex flex-wrap gap-2">
          {(profile.badges ?? []).map((badge) => {
            if (badge.type === "streak") {
              return (
                <StreakBadge
                  key={badge.label}
                  label={badge.label}
                  streakCount={profile.streakCount ?? 0}
                />
              );
            }
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
      )}
    </section>
  );
}

// 연속 출석 3일 이상이면 불꽃이 살짝 흔들리고(flicker), 7일 이상이면 더 크고 진한 코랄색으로 타오른다.
function StreakBadge({ label, streakCount }) {
  const isBlazing = streakCount >= 7;
  const isHot = streakCount >= 3;

  return (
    <span
      className={`flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm ${
        isBlazing
          ? "bg-[var(--color-coral)]/10 text-[var(--color-coral)]"
          : "bg-[var(--color-forest)]/5 text-[var(--color-forest)]"
      }`}
    >
      {isHot ? (
        <motion.span
          className="flex items-center"
          animate={{ scale: [1, isBlazing ? 1.15 : 1.08, 1], opacity: [0.85, 1, 0.85] }}
          transition={{ duration: isBlazing ? 0.9 : 1.3, repeat: Infinity, ease: "easeInOut" }}
        >
          <Flame
            size={isBlazing ? 18 : 14}
            fill={isBlazing ? "var(--color-coral)" : "none"}
            className={isBlazing ? "text-[var(--color-coral)]" : "text-[var(--color-honey)]"}
          />
        </motion.span>
      ) : (
        <Flame size={14} />
      )}
      {label}
    </span>
  );
}

// 책 1권을 먹였을 때 오르는 경험치. 백엔드 Lion.EXP_PER_FEED 와 같은 값이며 책과 무관하게
// 고정이다(구독 회원은 서버에서 2배로 들어오고, 화면은 서버 응답 exp 를 그대로 쓴다).
const EXP_PER_FEED = 40;

// 레벨 1당 필요한 경험치. 백엔드 Lion.EXP_PER_LEVEL 과 같아야 한다. 진행바 표시(exp % 100)에만 쓴다
// — 실제 레벨/경험치는 항상 서버 응답값을 그대로 쓰고 프론트에서 다시 계산하지 않는다.
const EXP_PER_LEVEL = 100;

function LionFeedingCard({ exp, setExp, level, setLevel }) {
  const [books, setBooks] = useState(null);
  const [isFeeding, setIsFeeding] = useState(false);
  const [levelUpInfo, setLevelUpInfo] = useState(null);
  const [feedAmount, setFeedAmount] = useState(0);
  const toast = useToast();

  useEffect(() => {
    let ignore = false;
    getFeedableBooks().then((data) => {
      if (!ignore) setBooks(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  // 실제 exp/level/growthStage는 항상 서버(POST /api/ai/lion/feed) 응답값을 그대로 쓴다 —
  // 프론트에서 따로 계산하면 서버 값과 어긋날 수 있다. +EXP 애니메이션 숫자만 낙관적으로 먼저 보여준다.
  //
  // feedLion(ai-service) 성공 후 markBookFed(catalog-service)를 이어서 부른다 — 두 서비스가
  // 동기 호출로 얽히지 않도록 프론트가 오케스트레이션한다. markBookFed가 실패해도(네트워크
  // 등) EXP는 이미 올라간 뒤라 되돌리지 않는다 — 다음 방문 시 목록에 다시 보여도 재-feed는
  // 멱등(EXP 안 오름)이라 무해하다.
  const handleDragEnd = (event) => {
    const { active, over } = event;
    if (over?.id !== "lion-mouth") return;
    const book = books?.find((b) => b.bookId === active.id);
    if (!book) return;

    setBooks((prev) => prev.filter((b) => b.bookId !== active.id));
    setIsFeeding(true);
    setFeedAmount(EXP_PER_FEED);

    feedLion(book.bookId)
      .then((status) => {
        setExp(status.exp);
        if (status.level > level) {
          const prevTier = getLionTier(level);
          const nextTier = getLionTier(status.level);
          setLevelUpInfo({
            level: status.level,
            tierChanged: prevTier.key !== nextTier.key,
            becameAdult: prevTier.key !== "adult" && nextTier.key === "adult",
          });
        }
        setLevel(status.level);
        markBookFed(book.bookId).catch(() => {
          // catalog 쪽 표시만 실패한 것 — EXP는 이미 올랐으니 조용히 넘어간다.
        });
      })
      .catch(() => {
        setBooks((prev) => [...prev, book]);
        toast.error("먹이기에 실패했어요. 잠시 후 다시 시도해주세요.");
      });

    setTimeout(() => setIsFeeding(false), 600);
  };

  return (
    <section className="relative rounded-2xl border-2 border-[var(--color-honey)]/40 bg-[var(--color-honey)]/5 p-8 shadow-[0_1px_3px_rgba(27,59,54,0.08)] sm:p-10">
      <h2 className="font-display mb-6 text-center text-2xl text-[var(--color-forest)]">
        사자 성장 & 완독한 책 먹이기
      </h2>

      <DndContext onDragEnd={handleDragEnd}>
        <div className="flex flex-col items-center gap-8">
          <LionDropZone exp={exp} isFeeding={isFeeding} level={level} feedAmount={feedAmount} />

          <div className="grid w-full grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
            {books === null ? (
              <>
                <Skeleton variant="rectangular" className="h-24 w-full" />
                <Skeleton variant="rectangular" className="h-24 w-full" />
              </>
            ) : books.length === 0 ? (
              <p className="col-span-2 py-4 text-center text-sm text-[var(--color-ink)] opacity-50 sm:col-span-3 md:col-span-4">
                eBook을 끝까지 읽으면 여기서 사자에게 먹일 수 있어요! 🦁
              </p>
            ) : (
              <AnimatePresence>
                {books.map((book) => (
                  <motion.div
                    key={book.bookId}
                    layout
                    exit={{ opacity: 0, scale: 0.7 }}
                    transition={{ duration: 0.3 }}
                  >
                    <DraggableBook
                      id={book.bookId}
                      bookTitle={book.bookTitle}
                      percentage={book.percentage}
                    />
                  </motion.div>
                ))}
              </AnimatePresence>
            )}
          </div>
        </div>
      </DndContext>

      <p className="mt-6 flex items-center justify-center gap-1.5 text-sm text-[var(--color-ink)] opacity-50">
        🐾 완독한 책을 사자 입으로 드래그하여 먹이기
      </p>

      <AnimatePresence>
        {levelUpInfo && (
          <LevelUpOverlay
            level={levelUpInfo.level}
            tierChanged={levelUpInfo.tierChanged}
            becameAdult={levelUpInfo.becameAdult}
            onClose={() => setLevelUpInfo(null)}
          />
        )}
      </AnimatePresence>
    </section>
  );
}

// 렌더 중 Math.random() 호출은 순수성 규칙에 걸리므로, 인덱스 기반의
// 결정적 분산 공식(골든 앵글 근사치인 137도 회전)으로 흩뿌린 것처럼 보이게 한다.
const CONFETTI_COLORS = ["var(--color-honey)", "var(--color-coral)", "var(--color-forest)"];
const CONFETTI_PARTICLES = Array.from({ length: 30 }).map((_, i) => ({
  id: i,
  x: ((i * 47) % 260) - 130,
  rotate: (i * 137) % 360,
  delay: (i % 10) * 0.04,
  color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
}));

function LevelUpOverlay({ level, tierChanged, becameAdult, onClose }) {
  const tier = getLionTier(level);

  useEffect(() => {
    const timer = setTimeout(onClose, 2500);
    return () => clearTimeout(timer);
  }, [onClose]);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--color-forest)]/60 backdrop-blur-sm"
    >
      <motion.div
        initial={{ scale: 0.8, opacity: 0, x: 0 }}
        animate={{ scale: 1, opacity: 1, x: [0, -6, 6, -4, 4, 0] }}
        exit={{ scale: 0.8, opacity: 0 }}
        transition={{
          scale: { type: "spring", stiffness: 260, damping: 20 },
          opacity: { duration: 0.3 },
          x: { duration: 0.3, ease: "easeInOut" },
        }}
        onClick={(e) => e.stopPropagation()}
        className="relative flex flex-col items-center gap-2 overflow-hidden rounded-2xl bg-white px-12 py-10 text-center shadow-xl"
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute right-3 top-3 rounded-full p-1 text-[var(--color-ink)] opacity-40 transition-opacity hover:opacity-70"
        >
          <X size={18} />
        </button>

        {CONFETTI_PARTICLES.map((p) => (
          <motion.span
            key={p.id}
            initial={{ x: 0, y: 0, opacity: 1, rotate: 0 }}
            animate={{ x: p.x, y: 180, opacity: 0, rotate: p.rotate }}
            transition={{ duration: 1.1, delay: p.delay, ease: "easeOut" }}
            className="pointer-events-none absolute left-1/2 top-10 h-2 w-2 rounded-sm"
            style={{ backgroundColor: p.color }}
          />
        ))}

        <motion.div
          animate={{ rotate: [0, -4, 4, -3, 3, 0] }}
          transition={{ duration: 1.2, repeat: Infinity, repeatDelay: 0.4 }}
        >
          <LionCharacter level={level} isLevelingUp crownEntrance={becameAdult} size={112} />
        </motion.div>

        <motion.p
          initial={{ scale: 0 }}
          animate={{ scale: [0, 1.2, 1] }}
          transition={{ type: "spring", stiffness: 300, damping: 12 }}
          className="font-display mt-2 text-2xl text-[var(--color-honey)]"
        >
          🎉 레벨 업!
        </motion.p>
        {tierChanged && (
          <motion.p
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="rounded-full bg-[var(--color-coral)]/15 px-3 py-1 text-xs font-medium text-[var(--color-coral)]"
          >
            ✨ 티어 승급!
          </motion.p>
        )}
        <p className="text-sm text-[var(--color-ink)] opacity-70">
          Lv.{level} {tier.label}이(가) 되었어요
        </p>
      </motion.div>
    </motion.div>
  );
}

function LionDropZone({ exp, isFeeding, level, feedAmount }) {
  const { setNodeRef, isOver } = useDroppable({ id: "lion-mouth" });
  const tier = getLionTier(level);
  const levelProgress = exp % EXP_PER_LEVEL;

  return (
    <div className="flex w-full max-w-sm flex-col items-center gap-4">
      <div
        ref={setNodeRef}
        className={`relative flex h-56 w-56 items-center justify-center rounded-full border-4 transition-all duration-200 ${
          isOver
            ? "scale-105 border-[var(--color-honey)] bg-[var(--color-honey)]/25"
            : "border-[var(--color-honey)]/40 bg-[var(--color-honey)]/10"
        }`}
      >
        <LionCharacter level={level} isFeeding={isFeeding} size={172} />
        <AnimatePresence>
          {isFeeding && (
            <motion.span
              initial={{ opacity: 0, y: 0, scale: 0.8 }}
              animate={{ opacity: 1, y: -36, scale: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.6, ease: "easeOut" }}
              className="font-display pointer-events-none absolute top-0 text-sm text-[var(--color-honey)]"
            >
              +{feedAmount} EXP
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      <p className="-mt-2 text-sm text-[var(--color-ink)] opacity-40">{tier.label}</p>

      {/* exp 는 누적값이라 그대로 쓰면 진행바가 100%를 넘는다. 현재 레벨 구간만 잘라 쓴다. */}
      <div className="w-full">
        <div className="h-3 w-full overflow-hidden rounded-full bg-[var(--color-forest)]/10">
          <motion.div
            className="h-full rounded-full bg-[var(--color-honey)]"
            animate={{ width: `${levelProgress}%` }}
            transition={{ duration: 0.5, ease: "easeOut" }}
          />
        </div>
        <p className="mt-1.5 text-center text-sm text-[var(--color-ink)] opacity-70">
          EXP: {levelProgress} / {EXP_PER_LEVEL}{" "}
          <span className="opacity-70">(다음 레벨까지 {EXP_PER_LEVEL - levelProgress})</span>
        </p>
      </div>
    </div>
  );
}

function DraggableBook({ id, bookTitle, percentage }) {
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
      <span className="line-clamp-2 text-sm font-medium text-[var(--color-ink)]">{bookTitle}</span>
      <span className="text-[11px] text-[var(--color-ink)] opacity-50">완독 {percentage}%</span>
      <span className="text-[11px] text-[var(--color-ink)] opacity-40">Drag me</span>
    </button>
  );
}

// citations[].score 는 0~1 이고 클수록 유사하다. 화면은 백분율 하나만 쓰므로
// 가장 가까운 근거를 대표값으로 삼는다. 근거가 없으면(grounded: false) 0 이다.
function toSimilarityPercent(citations) {
  if (!citations?.length) return 0;
  return Math.round(Math.max(...citations.map((c) => c.score)) * 100);
}

function LionRagCard() {
  const [question, setQuestion] = useState("");
  const [status, setStatus] = useState("idle"); // idle | loading | streaming | done
  const [displayedAnswer, setDisplayedAnswer] = useState("");
  const [similarity, setSimilarity] = useState(null);

  const handleAsk = async () => {
    if (!question.trim() || status === "loading" || status === "streaming") return;
    setStatus("loading");
    setDisplayedAnswer("");

    const result = await askLion(question);
    setSimilarity(toSimilarityPercent(result.citations));
    setStatus("streaming");

    const full = result.answer;
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
        <span className="text-sm font-normal text-[var(--color-ink)] opacity-40">
          (Spring AI RAG)
        </span>
      </h2>

      {/* 사자는 구매한 책의 본문만 근거로 답한다 — 내가 쓴 메모는 검색 대상이 아니다
          (WikiRagService가 user_summary 벡터를 배제한다). 메모는 아래 "내 메모" 섹션에서 본다. */}
      <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
        구매한 책의 본문에서 찾아 답해요. 어느 책의 어느 대목인지도 함께 알려줘요.
      </p>

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
          placeholder="객체지향 캡슐화 설명한 부분이 어느 책이야?"
          className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
        <Button
          type="submit"
          variant="primary"
          shimmer={false}
          disabled={isBusy}
          className="shrink-0 px-4"
        >
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

  // 카드/배송지 관리는 이 안에 임베드하지 않고 기존 독립 라우트를 그대로 쓴다
  // (CardsPage/AddressesPage는 자체 <main>과 섹션 레이아웃을 가진 독립 페이지라 탭 패널에
  // 끼워 넣기엔 구조가 맞지 않는다).
  useEffect(() => {
    if (activeTab === "cards") {
      navigate("/cards", { replace: true });
    } else if (activeTab === "addresses") {
      navigate("/addresses", { replace: true });
    }
  }, [activeTab, navigate]);

  const setTab = (id) => {
    if (id === "cards") {
      navigate("/cards");
      return;
    }
    if (id === "addresses") {
      navigate("/addresses");
      return;
    }
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("tab", id);
      return next;
    });
  };

  return (
    <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
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
        {activeTab === "subscription" && <SubscriptionTab />}
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

// 배송 레코드는 결제가 확정된 시점에만 생성된다(OrderService.createDelivery) — 결제 대기
// 중인 주문은 배송조회 자체가 불가능하다. 취소/반품은 백엔드가 PAID 상태에서만 허용한다
// (Order.cancel/requestReturn). 이 조건 외에 배송 진행 정도로 취소·반품 버튼을 더 나누지
// 않는다 — 백엔드가 실제로 검증하지 않는 규칙을 프론트가 임의로 만드는 셈이라서다.
function canViewDelivery(orderStatus) {
  return orderStatus !== "PENDING_PAYMENT";
}
function canCancelOrReturn(orderStatus) {
  return orderStatus === "PAID";
}

const ORDERS_PAGE_SIZE = 20;

function OrdersTab() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [orderPage, setOrderPage] = useState(null);
  const [isError, setIsError] = useState(false);
  const [busyOrderId, setBusyOrderId] = useState(null);
  // 취소/반품 성공 후 목록을 다시 부르기 위한 트리거. page가 그대로여도 useEffect가
  // 다시 돌아야 하므로 page와 별개의 의존성으로 둔다.
  const [reloadTick, setReloadTick] = useState(0);

  const [detailOrder, setDetailOrder] = useState(null);
  const [deliveryModal, setDeliveryModal] = useState(null); // { orderId, delivery: null | data }
  const [cancelTarget, setCancelTarget] = useState(null); // orderId
  const [returnTarget, setReturnTarget] = useState(null); // orderId
  const [returnReason, setReturnReason] = useState("");

  const loadOrders = useCallback(() => setReloadTick((t) => t + 1), []);

  useEffect(() => {
    let ignore = false;
    getMyOrders({ page, size: ORDERS_PAGE_SIZE })
      .then((data) => {
        if (!ignore) setOrderPage(data);
      })
      .catch(() => {
        if (!ignore) setIsError(true);
      });
    return () => {
      ignore = true;
    };
  }, [page, reloadTick]);

  const openDetail = (orderId) => {
    getOrder(orderId)
      .then(setDetailOrder)
      .catch(() => toast.error("주문 상세를 불러오지 못했어요."));
  };

  const openDelivery = (orderId) => {
    setDeliveryModal({ orderId, delivery: null });
    getOrderDelivery(orderId)
      .then((delivery) => setDeliveryModal({ orderId, delivery }))
      .catch(() => {
        toast.error("배송 정보를 불러오지 못했어요.");
        setDeliveryModal(null);
      });
  };

  const handleCancel = () => {
    const orderId = cancelTarget;
    setBusyOrderId(orderId);
    cancelOrder(orderId)
      .then(() => {
        toast.success("주문을 취소했어요.");
        setCancelTarget(null);
        loadOrders();
      })
      .catch(() => toast.error("주문 취소에 실패했어요."))
      .finally(() => setBusyOrderId(null));
  };

  const handleReturn = () => {
    if (!returnReason.trim()) {
      toast.error("반품/교환 사유를 입력해주세요.");
      return;
    }
    const orderId = returnTarget;
    setBusyOrderId(orderId);
    requestOrderReturn(orderId, returnReason.trim())
      .then(() => {
        toast.success("반품/교환을 접수했어요.");
        setReturnTarget(null);
        setReturnReason("");
        loadOrders();
      })
      .catch(() => toast.error("반품/교환 신청에 실패했어요."))
      .finally(() => setBusyOrderId(null));
  };

  if (!orderPage && !isError) return <TabSkeleton />;
  if (isError) {
    return (
      <p className="py-10 text-center text-sm text-[var(--color-coral)]">
        주문 목록을 불러오지 못했어요.
      </p>
    );
  }
  if (!orderPage || orderPage.content.length === 0) {
    return <EmptyState message="주문 내역이 없어요" />;
  }

  return (
    <div className="flex flex-col gap-3">
      {orderPage.content.map((order) => {
        const meta = ORDER_STATUS_META[order.orderStatus];
        const isBusy = busyOrderId === order.orderId;
        return (
          <div
            key={order.orderId}
            className="flex flex-col gap-3 rounded-xl border border-[var(--color-forest)]/10 p-4 sm:flex-row sm:items-center sm:justify-between"
          >
            <div>
              <p className="text-sm text-[var(--color-ink)] opacity-50">주문 #{order.orderId}</p>
              <p className="font-display mt-1 text-base text-[var(--color-ink)]">
                {order.totalAmount.toLocaleString()}원
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${meta.className}`}>
                {meta.label}
              </span>
              {canViewDelivery(order.orderStatus) && (
                <Button
                  variant="secondary"
                  size="sm"
                  shimmer={false}
                  onClick={() => openDelivery(order.orderId)}
                >
                  배송조회
                </Button>
              )}
              {canCancelOrReturn(order.orderStatus) && (
                <>
                  <Button
                    variant="ghost"
                    size="sm"
                    shimmer={false}
                    disabled={isBusy}
                    className="text-[var(--color-coral)]"
                    onClick={() => setCancelTarget(order.orderId)}
                  >
                    취소
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    shimmer={false}
                    disabled={isBusy}
                    onClick={() => setReturnTarget(order.orderId)}
                  >
                    반품/교환 신청
                  </Button>
                </>
              )}
              <Button
                variant="ghost"
                size="sm"
                shimmer={false}
                onClick={() => openDetail(order.orderId)}
              >
                상세보기
              </Button>
            </div>
          </div>
        );
      })}

      {orderPage.totalPages > 1 && (
        <div className="mt-2 flex items-center justify-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            shimmer={false}
            disabled={orderPage.first}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            이전
          </Button>
          <span className="text-sm text-[var(--color-ink)] opacity-70">
            {orderPage.number + 1} / {orderPage.totalPages}
          </span>
          <Button
            variant="ghost"
            size="sm"
            shimmer={false}
            disabled={orderPage.last}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}

      <Modal
        isOpen={!!detailOrder}
        onClose={() => setDetailOrder(null)}
        title={detailOrder ? `주문 #${detailOrder.orderId} 상세` : "주문 상세"}
        footer={
          <Button variant="ghost" onClick={() => setDetailOrder(null)}>
            닫기
          </Button>
        }
      >
        {detailOrder && (
          <div className="flex flex-col gap-4 text-sm">
            <div>
              <p className="mb-1.5 font-medium text-[var(--color-ink)] opacity-80">주문 품목</p>
              <ul className="flex flex-col gap-1.5">
                {detailOrder.items.map((item) => (
                  <li
                    key={item.orderItemId}
                    className="flex items-center justify-between rounded-lg bg-[var(--color-forest)]/5 px-3 py-2"
                  >
                    <span className="text-[var(--color-ink)]">
                      {item.bookTitle} × {item.quantity}
                    </span>
                    <span className="text-[var(--color-ink)] opacity-70">
                      {(item.unitPrice * item.quantity).toLocaleString()}원
                    </span>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <p className="mb-1 font-medium text-[var(--color-ink)] opacity-80">받는 분</p>
              <p className="text-[var(--color-ink)] opacity-70">
                {detailOrder.recipient?.name} · {detailOrder.recipient?.phone}
                <br />
                {detailOrder.recipient?.address}
              </p>
            </div>
            {detailOrder.payment && (
              <div>
                <p className="mb-1 font-medium text-[var(--color-ink)] opacity-80">결제</p>
                <p className="text-[var(--color-ink)] opacity-70">
                  {detailOrder.payment.paymentMethod} ·{" "}
                  {detailOrder.payment.amount.toLocaleString()}원
                </p>
              </div>
            )}
            {detailOrder.returnReason && (
              <div>
                <p className="mb-1 font-medium text-[var(--color-ink)] opacity-80">
                  반품/교환 사유
                </p>
                <p className="text-[var(--color-ink)] opacity-70">{detailOrder.returnReason}</p>
              </div>
            )}
          </div>
        )}
      </Modal>

      <Modal
        isOpen={!!deliveryModal}
        onClose={() => setDeliveryModal(null)}
        title="배송 조회"
        footer={
          <Button variant="ghost" onClick={() => setDeliveryModal(null)}>
            닫기
          </Button>
        }
      >
        {!deliveryModal?.delivery ? (
          <TabSkeleton />
        ) : (
          <div className="flex flex-col gap-2 text-sm text-[var(--color-ink)]">
            <p>
              <span className="opacity-60">배송 상태 </span>
              <span className="font-medium">
                {DELIVERY_STATUS_LABEL[deliveryModal.delivery.deliveryStatus]}
              </span>
            </p>
            <p className="opacity-70">
              택배사: {deliveryModal.delivery.courierCompany ?? "미배정"}
              <br />
              운송장번호: {deliveryModal.delivery.trackingNumber ?? "미배정"}
            </p>
          </div>
        )}
      </Modal>

      <Modal
        isOpen={!!cancelTarget}
        onClose={() => setCancelTarget(null)}
        title="주문을 취소할까요?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setCancelTarget(null)}>
              닫기
            </Button>
            <Button variant="coral" onClick={handleCancel} disabled={busyOrderId === cancelTarget}>
              주문 취소
            </Button>
          </>
        }
      >
        <p className="text-sm text-[var(--color-ink)] opacity-80">
          주문 #{cancelTarget}을(를) 취소하면 되돌릴 수 없어요.
        </p>
      </Modal>

      <Modal
        isOpen={!!returnTarget}
        onClose={() => {
          setReturnTarget(null);
          setReturnReason("");
        }}
        title="반품/교환 신청"
        footer={
          <>
            <Button
              variant="ghost"
              onClick={() => {
                setReturnTarget(null);
                setReturnReason("");
              }}
            >
              닫기
            </Button>
            <Button
              variant="primary"
              onClick={handleReturn}
              disabled={busyOrderId === returnTarget}
            >
              신청
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-2">
          <p className="text-sm text-[var(--color-ink)] opacity-80">주문 #{returnTarget}</p>
          <textarea
            value={returnReason}
            onChange={(e) => setReturnReason(e.target.value)}
            rows={3}
            placeholder="반품/교환 사유를 입력해주세요"
            className="w-full resize-none rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
        </div>
      </Modal>
    </div>
  );
}

function CouponsTab() {
  const [state, setState] = useState(null);
  const [isError, setIsError] = useState(false);

  useEffect(() => {
    let ignore = false;
    fetchCoupons()
      .then((data) => {
        if (!ignore) setState(data);
      })
      .catch(() => {
        if (!ignore) setIsError(true);
      });
    return () => {
      ignore = true;
    };
  }, []);

  if (!state && !isError) return <TabSkeleton />;
  if (isError) {
    return (
      <p className="py-10 text-center text-sm text-[var(--color-coral)]">
        쿠폰 목록을 불러오지 못했어요.
      </p>
    );
  }
  if (state.length === 0) return <EmptyState message="사용 가능한 쿠폰이 없어요" />;

  // 만료/사용 분기는 없앴다. 백엔드가 미사용 + 미만료 쿠폰만 내려주므로
  // 만료된 항목이 이 목록에 도달할 경로가 없다.
  return (
    <div className="flex flex-col gap-5">
      <ul className="flex flex-col gap-2">
        {state.map((coupon) => (
          <li
            key={coupon.memberCouponId}
            className="flex items-center justify-between rounded-xl border border-dashed border-[var(--color-honey)]/50 px-4 py-3"
          >
            <div>
              <p className="text-sm font-medium text-[var(--color-ink)]">{coupon.couponName}</p>
              <p className="text-sm text-[var(--color-ink)] opacity-50">
                ~{coupon.expiresAt?.slice(0, 10)}까지
              </p>
            </div>
            <span className="text-xs font-medium text-[var(--color-coral)]">사용 가능</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

const SUBSCRIPTION_PLAN_LABELS = { MONTHLY: "월간 구독", YEARLY: "연간 구독" };
const SUBSCRIPTION_STATUS_LABELS = { ACTIVE: "구독 중", CANCELLED: "해지됨", EXPIRED: "만료됨" };

// ProductListPage/ProductDetailPage의 구독 CTA와 같은 subscribe("MONTHLY")/cancelSubscription()
// 을 그대로 쓴다. 저 두 화면은 react-query로 ["mySubscription"]을 관리하지만, 이 파일은
// 다른 탭(OrdersTab/CouponsTab 등) 전부 useState+수동 refetch라 그 컨벤션을 따른다 — 페이지를
// 이동하면 어차피 새로 mount되어 새로 조회하므로 캐시를 공유하지 않아도 불일치가 남지 않는다.
function SubscriptionTab() {
  const toast = useToast();
  const navigate = useNavigate();
  const [subscription, setSubscription] = useState(null);
  const [isError, setIsError] = useState(false);
  const [isMutating, setIsMutating] = useState(false);

  const loadSubscription = useCallback(() => {
    getMySubscription()
      .then((data) => {
        setSubscription(data);
        setIsError(false);
      })
      .catch(() => setIsError(true));
  }, []);

  useEffect(() => {
    loadSubscription();
  }, [loadSubscription]);

  // 구독은 결제를 거친다 — 여기서 만들지 않고 /checkout 으로 보낸다. 결제가 확정되면
  // order-service 가 구독을 활성화한다(constants/subscription.ts 참고).
  const handleSubscribe = () =>
    navigate("/checkout", { state: { items: [subscriptionCheckoutItem()] } });

  const handleCancel = async () => {
    setIsMutating(true);
    try {
      const data = await cancelSubscription();
      setSubscription(data);
      toast.success("구독을 취소했습니다");
    } catch {
      toast.error("구독 취소에 실패했습니다. 잠시 후 다시 시도해주세요.");
    } finally {
      setIsMutating(false);
    }
  };

  if (!subscription && !isError) return <TabSkeleton />;
  if (isError) {
    return (
      <p className="py-10 text-center text-sm text-[var(--color-coral)]">
        구독 정보를 불러오지 못했어요.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3 rounded-xl border border-dashed border-[var(--color-honey)]/50 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm font-medium text-[var(--color-ink)]">🎁 정기 구독</p>
          {subscription.status ? (
            <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-[var(--color-ink)] opacity-70">
              <span
                className={subscription.isActive ? "font-medium text-[var(--color-forest)]" : ""}
              >
                {SUBSCRIPTION_STATUS_LABELS[subscription.status] ?? subscription.status}
              </span>
              <span>
                {SUBSCRIPTION_PLAN_LABELS[subscription.planType] ?? subscription.planType}
              </span>
              {subscription.startedAt && <span>시작일 {subscription.startedAt.slice(0, 10)}</span>}
              {subscription.expiresAt && (
                <span>
                  {subscription.isActive ? "다음 결제일" : "만료일"}{" "}
                  {subscription.expiresAt.slice(0, 10)}
                </span>
              )}
            </div>
          ) : (
            <p className="mt-1.5 text-sm text-[var(--color-ink)] opacity-50">
              아직 구독 중이 아니에요. 구독하면 웹툰 요약 컷 열람 + 사자 먹이 2배 적립 혜택을 받을
              수 있어요.
            </p>
          )}
        </div>
        {subscription.isActive ? (
          <Button
            variant="ghost"
            size="sm"
            shimmer={false}
            loading={isMutating}
            className="shrink-0 text-[var(--color-coral)]"
            onClick={handleCancel}
          >
            구독 취소
          </Button>
        ) : (
          <Button
            variant="secondary"
            size="sm"
            shimmer={false}
            loading={isMutating}
            className="shrink-0"
            onClick={handleSubscribe}
          >
            구독하기 &gt;
          </Button>
        )}
      </div>
    </div>
  );
}

function ReturnsTab() {
  const [requests, setRequests] = useState(null);

  useEffect(() => {
    if (!USE_MOCK) return;
    let ignore = false;
    fetchReturnRequests().then((data) => {
      if (!ignore) setRequests(data);
    });
    return () => {
      ignore = true;
    };
  }, []);

  if (!USE_MOCK) return <EmptyState message="취소/교환/반품/환불 기능은 준비 중이에요" />;
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

function EmptyState({ message }) {
  return <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-40">{message}</p>;
}

function ReviewsSection() {
  const toast = useToast();
  const [reviews, setReviews] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [editingReview, setEditingReview] = useState(null);
  const [editForm, setEditForm] = useState({ rating: 5, content: "" });
  const [isDeleting, setIsDeleting] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let ignore = false;
    fetchReviews()
      .then((data) => {
        if (!ignore) setReviews(data);
      })
      .catch(() => {
        if (!ignore) {
          setReviews([]);
          setLoadError(true);
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  const handleDelete = async () => {
    if (!pendingDelete || isDeleting) return;
    setIsDeleting(true);
    try {
      await deleteReview(pendingDelete.id);
      setReviews((prev) => prev.filter((r) => r.id !== pendingDelete.id));
      setPendingDelete(null);
      toast.success("리뷰가 삭제되었습니다.");
    } catch {
      toast.error("리뷰를 삭제하지 못했습니다.");
    } finally {
      setIsDeleting(false);
    }
  };

  const openEdit = (review) => {
    setEditingReview(review);
    setEditForm({ rating: review.rating, content: review.content });
  };

  const handleSaveEdit = async () => {
    if (!editForm.content.trim()) {
      toast.error("리뷰 내용을 입력해주세요.");
      return;
    }
    if (!editingReview || isSaving) return;
    setIsSaving(true);
    try {
      await updateReview(editingReview.id, {
        rating: editForm.rating,
        content: editForm.content.trim(),
      });
      setReviews((prev) =>
        prev.map((r) =>
          r.id === editingReview.id
            ? { ...r, rating: editForm.rating, content: editForm.content.trim() }
            : r,
        ),
      );
      setEditingReview(null);
      toast.success("리뷰가 수정되었습니다.");
    } catch {
      toast.error("리뷰를 수정하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <section className="mt-6 rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <h2 className="font-display mb-5 text-lg text-[var(--color-forest)]">
        내가 작성한 한줄평 & 도서 리뷰 관리
      </h2>

      {reviews === null ? (
        <TabSkeleton />
      ) : loadError ? (
        <EmptyState message="내가 작성한 리뷰를 불러오지 못했습니다" />
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
                    <Button
                      variant="secondary"
                      size="sm"
                      shimmer={false}
                      onClick={() => openEdit(review)}
                    >
                      수정
                    </Button>
                    <Button
                      variant="coral"
                      size="sm"
                      shimmer={false}
                      onClick={() => setPendingDelete(review)}
                    >
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
            <Button variant="coral" disabled={isDeleting} onClick={handleDelete}>
              {isDeleting ? "삭제 중..." : "삭제"}
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
            <Button variant="primary" disabled={isSaving} onClick={handleSaveEdit}>
              {isSaving ? "저장 중..." : "저장"}
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <div>
            <p className="mb-1.5 text-sm font-medium text-[var(--color-ink)] opacity-80">
              {editingReview?.book}
            </p>
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
