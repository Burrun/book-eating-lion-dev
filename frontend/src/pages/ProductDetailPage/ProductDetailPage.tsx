import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getBook, getEbookAccess, getSynopsisDetail, getWebtoonCuts } from "../../api/books.ts";
import { createReview, deleteReview, getReviews, updateReview } from "../../api/reviews.ts";
import { addToWishlist, getWishlist, removeFromWishlist } from "../../api/wishlist.ts";
import { getMyProfile, getMySubscription } from "../../api/member.ts";
import { subscriptionCheckoutItem } from "../../constants/subscription.ts";
import { addToCart } from "../../api/cart.js";
import { feedLion } from "../../api/mypage.js";
import { getBookMemo, markMemoFed, saveBookMemo } from "../../api/bookMemo.ts";
import { useToast } from "../../components/Toast.jsx";
import EbookViewer from "../../components/EbookViewer.jsx";
import { useReadingProgress } from "../../hooks/useReadingProgress.js";

const REVIEW_CONTENT_MAX_LENGTH = 1000;

// 완독 판정 임계값. epub.js의 atEnd(파일의 물리적 끝)를 단독 기준으로 쓰면, 구텐베르크
// 소스 EPUB처럼 소설 본문 뒤에 라이선스 고지문이 같은 스파인 항목에 이어붙은 책에서는
// 사용자가 라이선스 텍스트까지 넘기지 않는 한 영영 100%가 안 된다(실제로 프랑켄슈타인/
// 이상한 나라의 앨리스 둘 다 이 구조를 확인함). 라이선스 재배포 요건상 EPUB 파일은 못
// 건드리므로, "본문을 사실상 다 읽었다"고 볼 수 있는 95% 이상이면 atEnd 여부와 무관하게
// 완독으로 취급한다. EbookViewer.jsx가 atEnd일 때 percentage를 100으로 저장하므로, 이
// 임계값 하나로 "percentage >= 95 OR atEnd" 조건이 그대로 구현된다(atEnd → 항상 100 →
// 95 이상).
const COMPLETION_PERCENTAGE_THRESHOLD = 95;

export default function ProductDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { initialPercentage: storedReadingPercentage } = useReadingProgress(id) as {
    initialPercentage: number | null;
  };
  // EbookViewer는 useReadingProgress를 별도 훅 인스턴스로 호출한다 — 뷰어 안에서 저장해도
  // 이 페이지의 훅 인스턴스는 리렌더되지 않고, 로컬 저장도 500ms 디바운스라 뷰어를 바로
  // 닫으면 그마저 반영 전일 수 있다. EbookViewer가 relocated마다 즉시 넘겨주는 값을 여기
  // 저장해두고, 있으면 그 값을(없으면 훅이 읽어온 저장값을) 쓴다 — 새로고침 없이 뷰어를
  // 닫자마자 완독 요약 메모가 뜨게 하기 위해서다.
  const [livePercentage, setLivePercentage] = useState<number | null>(null);
  const [livePercentageBookId, setLivePercentageBookId] = useState(id);
  if (livePercentageBookId !== id) {
    setLivePercentageBookId(id);
    setLivePercentage(null);
  }
  const readingPercentage = livePercentage ?? storedReadingPercentage;
  // Toast.jsx는 checkJs:false라 createContext(null)+throw 패턴이 TS 쪽에서 반환 타입을
  // never로 좁힌다(CardsPage.tsx와 동일한 이슈). 여기서만 실제 형태로 타입을 명시한다.
  const toast = useToast() as {
    success: (message: string) => void;
    error: (message: string) => void;
  };

  const {
    data: book,
    isPending,
    isError,
  } = useQuery({
    queryKey: ["book", id],
    queryFn: () => getBook(id!),
    enabled: Boolean(id),
  });

  // 구독 회원 전용 웹툰 요약 컷. 조회 실패/로딩 중에는 비구독으로 취급한다(fail-safe).
  // true: 웹툰 요약 컷 / false: 줄거리 텍스트 + 구독 유도
  const { data: subscription } = useQuery({
    queryKey: ["mySubscription"],
    queryFn: getMySubscription,
  });
  const hasWebtoonAccess = subscription?.isActive ?? false;

  // 컷은 구독 회원에게만 보여주므로 그때만 조회한다.
  const { data: webtoonCuts } = useQuery({
    queryKey: ["webtoonCuts", id],
    queryFn: () => getWebtoonCuts(id!),
    enabled: Boolean(id) && hasWebtoonAccess,
  });

  // 상세 응답에는 리뷰가 없다(BookDetailResponse). 리뷰는 전용 API로 따로 가져온다.
  const { data: reviewPage } = useQuery({
    queryKey: ["reviews", id],
    queryFn: () => getReviews(id!),
    enabled: Boolean(id),
  });

  const { data: synopsisDetail } = useQuery({
    queryKey: ["bookSynopsisDetail", id],
    queryFn: () => getSynopsisDetail(id!),
    enabled: Boolean(id),
  });

  // 리뷰 목록에서 "내 리뷰"에만 수정/삭제 버튼을 보여주기 위해 내 memberId(Cognito sub)가 필요하다.
  const { data: myProfile } = useQuery({ queryKey: ["myProfile"], queryFn: getMyProfile });

  const [draftRating, setDraftRating] = useState(5);
  const [draftText, setDraftText] = useState("");
  // null이면 새 리뷰 작성 폼, 값이 있으면 그 리뷰를 인라인으로 수정 중.
  const [editingReviewId, setEditingReviewId] = useState<string | null>(null);
  const [editRating, setEditRating] = useState(5);
  const [editText, setEditText] = useState("");
  const [isEbookOpen, setIsEbookOpen] = useState(false);
  const [ebookUrl, setEbookUrl] = useState<string | null>(null);
  const isBookComplete =
    readingPercentage != null && readingPercentage >= COMPLETION_PERCENTAGE_THRESHOLD;

  const { data: myMemo, isSuccess: memoLoaded } = useQuery({
    queryKey: ["bookMemo", id],
    queryFn: () => getBookMemo(id!),
    enabled: Boolean(id) && isBookComplete,
  });

  const [summaryText, setSummaryText] = useState("");
  // id(도서)가 바뀌거나 서버 메모가 막 로드됐을 때만 입력창을 그 값으로 채운다 —
  // EbookViewer.jsx의 renderedBookId와 같은 "렌더 중 조정" 패턴. memoLoaded가 되기 전(또는
  // 로그인 안 해서 계속 로딩 중)에는 건드리지 않아, 리페치로 사용자가 타이핑 중인 내용을
  // 덮어쓰지 않는다.
  const [summarySyncedFor, setSummarySyncedFor] = useState<string | null | undefined>(undefined);
  if (memoLoaded && summarySyncedFor !== id) {
    setSummarySyncedFor(id ?? null);
    setSummaryText(myMemo?.memoText ?? "");
  }

  const [isSavingSummary, setIsSavingSummary] = useState(false);

  async function handleSaveSummary() {
    if (!id || !summaryText.trim() || !book) return;
    setIsSavingSummary(true);
    try {
      const saved = await saveBookMemo(id, summaryText.trim());
      queryClient.invalidateQueries({ queryKey: ["bookMemo", id] });
      toast.success("요약 메모를 저장했어요.");

      // 이미 사자에게 먹인 메모를 재작성한 경우 — EXP는 다시 안 오르지만(feedLion이 멱등),
      // RAG가 아는 내용은 최신이어야 하므로 인덱스만 다시 갱신한다.
      if (saved.fedAt) {
        await feedLion(Number(id), book.title, summaryText.trim());
        await markMemoFed(id).catch(() => {
          // catalog 쪽 표시 갱신만 실패한 것 — 인덱스는 이미 최신으로 반영됐다.
        });
      }
    } catch {
      toast.error("요약 메모를 저장하지 못했어요. 잠시 후 다시 시도해주세요.");
    } finally {
      setIsSavingSummary(false);
    }
  }

  const { data: wishlist = [] } = useQuery({
    queryKey: ["wishlist"],
    queryFn: getWishlist,
  });
  const isWishlisted = wishlist.some((item) => item.id === String(id));

  const wishlistMutation = useMutation({
    mutationFn: ({
      bookId,
      currentlyWishlisted,
    }: {
      bookId: string;
      currentlyWishlisted: boolean;
    }) => (currentlyWishlisted ? removeFromWishlist(bookId) : addToWishlist(bookId)),
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["wishlist"] });
      toast.success(
        variables.currentlyWishlisted ? "찜 목록에서 삭제했습니다." : "찜 목록에 추가했습니다.",
      );
    },
    onError: () => toast.error("찜 상태를 변경하지 못했습니다. 로그인 상태를 확인해주세요."),
  });

  const ebookAccessMutation = useMutation({
    mutationFn: () => getEbookAccess(id!),
    onSuccess: (access) => {
      if (!access.ebookAvailable || !access.presignedUrl) {
        toast.error("아직 eBook이 준비되지 않은 도서입니다.");
        return;
      }
      setEbookUrl(access.presignedUrl);
      setIsEbookOpen(true);
    },
    onError: (err: unknown) => {
      const code = (err as { code?: string } | null)?.code;
      toast.error(
        code === "EBOOK_OWNERSHIP_REQUIRED"
          ? "구매 확정된 도서만 열람할 수 있어요."
          : "eBook을 열지 못했습니다. 로그인 상태를 확인한 뒤 다시 시도해주세요.",
      );
    },
  });

  function handleOpenEbook() {
    setEbookUrl(null);
    ebookAccessMutation.mutate();
  }

  function handleCloseEbook() {
    setIsEbookOpen(false);
    setEbookUrl(null);
  }

  // addToCart(api/cart.js)가 로그인/게스트 분기를 내부에서 이미 처리하므로 여기서는 그대로 호출만 한다.
  const addToCartMutation = useMutation({
    mutationFn: () => addToCart(Number(id), 1),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cart"] });
      toast.success("장바구니에 담았습니다");
    },
    onError: () => {
      toast.error("장바구니에 담지 못했습니다. 잠시 후 다시 시도해주세요.");
    },
  });

  // 구독은 결제를 거친다(MONTHLY 고정). 여기서 만들지 않고 /checkout 으로 보내며,
  // 결제가 확정되면 order-service 가 구독을 활성화한다(constants/subscription.ts 참고).
  const goToSubscriptionCheckout = () =>
    navigate("/checkout", { state: { items: [subscriptionCheckoutItem()] } });

  function invalidateReviews() {
    queryClient.invalidateQueries({ queryKey: ["reviews", id] });
    // averageRating/reviewCount는 BookDetailResponse에도 있어(초기 표시용) 책 상세도 같이 갱신한다.
    queryClient.invalidateQueries({ queryKey: ["book", id] });
  }

  // 구매 확정 이력(review_permissions)이 없으면 서버가 403 REVIEW_PERMISSION_REQUIRED를
  // 던진다 — 사전에 알 방법이 없는 조건이라(계약에 "작성 가능 여부" API가 없음) 제출 후
  // 이 코드로만 구분해서 안내한다.
  const createReviewMutation = useMutation({
    mutationFn: () => createReview(id!, { rating: draftRating, content: draftText.trim() }),
    onSuccess: () => {
      invalidateReviews();
      setDraftText("");
      setDraftRating(5);
      toast.success("리뷰가 등록되었습니다");
    },
    onError: (err: unknown) => {
      const code = (err as { code?: string } | null)?.code;
      toast.error(
        code === "REVIEW_PERMISSION_REQUIRED"
          ? "구매 확정 후 리뷰를 작성할 수 있어요"
          : "리뷰 등록에 실패했습니다. 잠시 후 다시 시도해주세요.",
      );
    },
  });

  const updateReviewMutation = useMutation({
    mutationFn: ({
      reviewId,
      rating,
      content,
    }: {
      reviewId: string;
      rating: number;
      content: string;
    }) => updateReview(reviewId, { rating, content }),
    onSuccess: () => {
      invalidateReviews();
      setEditingReviewId(null);
      toast.success("리뷰가 수정되었습니다");
    },
    onError: () => {
      toast.error("리뷰 수정에 실패했습니다. 잠시 후 다시 시도해주세요.");
    },
  });

  const deleteReviewMutation = useMutation({
    mutationFn: (reviewId: string) => deleteReview(reviewId),
    onSuccess: () => {
      invalidateReviews();
      toast.success("리뷰가 삭제되었습니다");
    },
    onError: () => {
      toast.error("리뷰 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
    },
  });

  function handleSubmitReview() {
    if (!draftText.trim()) {
      toast.error("한줄평을 입력해주세요.");
      return;
    }
    createReviewMutation.mutate();
  }

  function startEditingReview(reviewId: string, rating: number, text: string) {
    setEditingReviewId(reviewId);
    setEditRating(rating);
    setEditText(text);
  }

  function handleSaveEdit(reviewId: string) {
    if (!editText.trim()) {
      toast.error("한줄평을 입력해주세요.");
      return;
    }
    updateReviewMutation.mutate({ reviewId, rating: editRating, content: editText.trim() });
  }

  if (isPending) {
    return (
      <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
        <div className="skeleton-shimmer h-80 rounded-2xl" />
        <div className="skeleton-shimmer h-48 rounded-2xl" />
      </main>
    );
  }

  if (isError || !book) {
    return (
      <main className="mx-auto flex max-w-6xl flex-col items-center gap-3 px-4 py-16">
        <p className="text-4xl">🦁</p>
        <p className="text-sm text-forest/60">도서 정보를 불러오지 못했습니다.</p>
        <Link
          to="/"
          className="rounded-full bg-forest px-5 py-2 text-sm font-semibold text-paper transition hover:bg-forest-light"
        >
          목록으로 돌아가기 &gt;
        </Link>
      </main>
    );
  }

  const reviews = reviewPage?.items ?? [];
  // 리뷰 목록 API의 totalElements가 book.reviewCount보다 최신이다(리뷰 목록은 이 페이지에서
  // 작성/수정/삭제 직후 즉시 무효화되지만, book 상세는 상황에 따라 갱신이 한 박자 늦을 수 있다).
  const reviewCount = reviewPage?.totalElements ?? book.reviewCount;

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="flex flex-col gap-6 rounded-2xl border border-forest/10 bg-white p-6 sm:flex-row">
        <div className="flex h-72 w-full shrink-0 items-center justify-center overflow-hidden rounded-xl border border-forest/10 bg-paper text-6xl sm:w-56">
          {book.coverImageUrl ? (
            <img src={book.coverImageUrl} alt={book.title} className="h-full w-full object-cover" />
          ) : (
            "📖"
          )}
        </div>
        <div className="flex flex-1 flex-col gap-2">
          <h1 className="text-2xl font-bold">{book.title}</h1>
          <p className="text-sm text-forest/60">
            저자: {book.author} | 출판사: {book.publisher} | ISBN: {book.isbn}
          </p>
          <p className="text-lg font-semibold text-coral">{book.price.toLocaleString()}원</p>
          <p className="text-sm text-forest/60">
            ⭐ {book.rating}점 (리뷰 {reviewCount}개) | {book.shippingNote}
          </p>
          <div className="mt-3 flex flex-wrap items-start gap-3">
            <button
              onClick={() => addToCartMutation.mutate()}
              disabled={addToCartMutation.isPending}
              className="rounded-full bg-forest px-6 py-2.5 font-semibold text-paper transition hover:bg-forest-light disabled:opacity-60"
            >
              {addToCartMutation.isPending ? "담는 중..." : "🛒 장바구니"}
            </button>
            <button
              onClick={() =>
                wishlistMutation.mutate({
                  bookId: String(id!),
                  currentlyWishlisted: isWishlisted,
                })
              }
              disabled={wishlistMutation.isPending}
              className="rounded-full bg-honey/25 px-6 py-2.5 font-semibold text-forest transition hover:bg-honey/40 disabled:opacity-60"
            >
              {wishlistMutation.isPending ? "처리 중..." : isWishlisted ? "❤️ 찜 해제" : "♡ 찜하기"}
            </button>
            {book.ebookAvailable ? (
              <div className="flex flex-col gap-1.5">
                <button
                  onClick={handleOpenEbook}
                  disabled={ebookAccessMutation.isPending}
                  className="rounded-full border-2 border-forest px-6 py-2.5 font-semibold text-forest transition hover:bg-forest hover:text-paper"
                >
                  {ebookAccessMutation.isPending ? "권한 확인 중..." : "📱 ebook 보기"}
                </button>
                {readingPercentage != null && (
                  <div className="flex flex-col gap-1">
                    <span className="text-xs text-forest/60">📖 {readingPercentage}% 읽음</span>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-forest/10">
                      <div
                        className="h-full rounded-full bg-honey transition-[width] duration-500 ease-out"
                        style={{ width: `${readingPercentage}%` }}
                      />
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <button
                disabled
                className="cursor-not-allowed rounded-full border-2 border-forest/15 px-6 py-2.5 font-semibold text-forest/40"
              >
                📱 ebook 준비중
              </button>
            )}
          </div>
        </div>
      </section>

      {isBookComplete && (
        <section className="flex flex-col gap-3 rounded-2xl border border-forest/10 bg-white p-6">
          <h2 className="text-xl font-bold">🦁 완독 요약 메모</h2>
          <p className="text-sm text-forest/60">
            이 책의 내용을 요약해서 남겨주세요. 사자에게 먹이면 나중에 "사자에게 물어보기"에서 이
            요약을 근거로 답해줘요.
          </p>
          <textarea
            value={summaryText}
            onChange={(e) => setSummaryText(e.target.value)}
            rows={5}
            placeholder="이 책은 어떤 내용이었나요? 핵심 내용을 요약해서 적어주세요."
            className="w-full resize-y rounded-lg border border-forest/20 px-4 py-2.5 outline-none focus:border-forest"
          />
          <button
            onClick={handleSaveSummary}
            disabled={!summaryText.trim() || isSavingSummary}
            className="self-end rounded-lg bg-forest px-6 py-2.5 font-semibold text-paper transition hover:bg-forest-light disabled:opacity-60"
          >
            {isSavingSummary ? "저장 중..." : "요약 저장"}
          </button>
        </section>
      )}

      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        {hasWebtoonAccess ? (
          <>
            <h2 className="text-xl font-bold">🎨 웹툰 요약 컷</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              {(webtoonCuts ?? []).map((cut) => (
                <div key={cut.id} className="overflow-hidden rounded-xl border border-forest/10">
                  <div className="flex h-48 items-center justify-center bg-paper text-4xl">🖼️</div>
                  <p className="p-3 text-sm text-forest/70">{cut.caption}</p>
                </div>
              ))}
            </div>
          </>
        ) : (
          <>
            <h2 className="text-xl font-bold">📖 줄거리</h2>
            <p className="text-sm leading-relaxed text-forest/80">
              {synopsisDetail?.detailedSynopsis || book.synopsis}
            </p>
            <div className="flex flex-col gap-3 rounded-xl border border-honey/40 bg-honey/15 p-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm font-semibold text-forest">
                🎨 구독 회원이 되면 이 책의 웹툰 요약 컷을 볼 수 있어요
              </p>
              <button
                onClick={goToSubscriptionCheckout}
                className="shrink-0 rounded-full bg-forest px-5 py-2 text-sm font-semibold text-paper transition hover:bg-forest-light"
              >
                구독하기 &gt;
              </button>
            </div>
          </>
        )}
      </section>

      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <h2 className="text-xl font-bold">⭐ 한줄평 &amp; 리뷰 작성</h2>
        <div className="flex flex-col gap-3 rounded-xl border border-forest/10 p-4">
          <div className="flex gap-1 text-2xl">
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={star}
                type="button"
                onClick={() => setDraftRating(star)}
                aria-label={`${star}점`}
                className={star <= draftRating ? "text-honey" : "text-forest/20"}
              >
                ★
              </button>
            ))}
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <input
              type="text"
              value={draftText}
              onChange={(e) => setDraftText(e.target.value)}
              maxLength={REVIEW_CONTENT_MAX_LENGTH}
              placeholder="한줄평을 입력하세요..."
              className="flex-1 rounded-lg border border-forest/20 px-4 py-2.5 outline-none focus:border-forest"
            />
            <button
              onClick={handleSubmitReview}
              disabled={createReviewMutation.isPending}
              className="shrink-0 rounded-lg bg-forest px-6 py-2.5 font-semibold text-paper transition hover:bg-forest-light disabled:opacity-60"
            >
              {createReviewMutation.isPending ? "등록 중..." : "등록하기"}
            </button>
          </div>
        </div>

        <div className="flex flex-col gap-3">
          {reviews.map((review) => {
            const isMine = Boolean(myProfile) && review.memberId === myProfile?.id;
            const isEditing = editingReviewId === review.id;

            if (isEditing) {
              return (
                <div key={review.id} className="flex flex-col gap-3 rounded-xl bg-paper p-4">
                  <div className="flex gap-1 text-xl">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <button
                        key={star}
                        type="button"
                        onClick={() => setEditRating(star)}
                        aria-label={`${star}점`}
                        className={star <= editRating ? "text-honey" : "text-forest/20"}
                      >
                        ★
                      </button>
                    ))}
                  </div>
                  <input
                    type="text"
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    maxLength={REVIEW_CONTENT_MAX_LENGTH}
                    className="rounded-lg border border-forest/20 px-4 py-2.5 outline-none focus:border-forest"
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleSaveEdit(review.id)}
                      disabled={updateReviewMutation.isPending}
                      className="rounded-lg bg-forest px-4 py-2 text-sm font-semibold text-paper transition hover:bg-forest-light disabled:opacity-60"
                    >
                      {updateReviewMutation.isPending ? "저장 중..." : "저장"}
                    </button>
                    <button
                      onClick={() => setEditingReviewId(null)}
                      className="rounded-lg border border-forest/20 px-4 py-2 text-sm font-semibold text-forest/70 transition hover:bg-forest/5"
                    >
                      취소
                    </button>
                  </div>
                </div>
              );
            }

            return (
              <div key={review.id} className="rounded-xl bg-paper p-4">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-semibold">
                    {review.author} {"⭐".repeat(review.rating)} | {review.date}
                  </p>
                  {isMine && (
                    <div className="flex shrink-0 gap-2 text-xs">
                      <button
                        onClick={() => startEditingReview(review.id, review.rating, review.text)}
                        className="text-forest/60 hover:underline"
                      >
                        수정
                      </button>
                      <button
                        onClick={() => deleteReviewMutation.mutate(review.id)}
                        disabled={deleteReviewMutation.isPending}
                        className="text-coral hover:underline disabled:opacity-60"
                      >
                        삭제
                      </button>
                    </div>
                  )}
                </div>
                <p className="mt-1 text-sm text-forest/80">{review.text}</p>
              </div>
            );
          })}
        </div>
      </section>

      <EbookViewer
        isOpen={isEbookOpen}
        onClose={handleCloseEbook}
        url={ebookUrl}
        title={book.title}
        bookId={id}
        onProgressChange={setLivePercentage}
      />
    </main>
  );
}
