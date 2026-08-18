import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getBook, getEbookAccess, getWebtoonCuts } from "../../api/books.ts";
import { getReviews } from "../../api/reviews.ts";
import { getMySubscription } from "../../api/member.ts";
import { addToCart } from "../../api/cart.js";
import { useToast } from "../../components/Toast.jsx";
import EbookViewer from "../../components/EbookViewer.jsx";
import { useReadingProgress } from "../../hooks/useReadingProgress.js";
import type { Review } from "../../types/book.ts";

export default function ProductDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const { initialPercentage: readingPercentage } = useReadingProgress(id) as {
    initialPercentage: number | null;
  };
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

  // 작성한 리뷰는 아직 서버로 보내지 않으므로 조회 결과 위에 얹어서 보여준다.
  const [addedReviews, setAddedReviews] = useState<Review[]>([]);
  const [draftRating, setDraftRating] = useState(5);
  const [draftText, setDraftText] = useState("");
  const [isEbookOpen, setIsEbookOpen] = useState(false);
  const [ebookUrl, setEbookUrl] = useState<string | null>(null);

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
    onError: () => {
      toast.error("eBook을 열지 못했습니다. 로그인 상태를 확인한 뒤 다시 시도해주세요.");
    },
  });

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

  function handleSubmitReview() {
    if (!draftText.trim()) return;
    setAddedReviews((prev) => [
      {
        id: `local-${Date.now()}`,
        author: "나",
        rating: draftRating,
        date: new Date().toISOString().slice(0, 10),
        text: draftText.trim(),
      },
      ...prev,
    ]);
    setDraftText("");
    setDraftRating(5);
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

  const reviews = [...addedReviews, ...(reviewPage?.items ?? [])];
  // 백엔드 상세 응답에 리뷰 수가 없어 매퍼가 0으로 채운다. 리뷰 목록의 totalElements 로 대체한다.
  const reviewCount = reviewPage?.totalElements ?? book.reviewCount;

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="flex flex-col gap-6 rounded-2xl border border-forest/10 bg-white p-6 sm:flex-row">
        <div className="flex h-72 w-full shrink-0 items-center justify-center rounded-xl border border-forest/10 bg-paper text-6xl sm:w-56">
          📖
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
            <button className="rounded-full bg-honey/25 px-6 py-2.5 font-semibold text-forest transition hover:bg-honey/40">
              ❤️ 찜하기
            </button>
            {book.ebookAvailable ? (
              <div className="flex flex-col gap-1.5">
                <button
                  onClick={() => ebookAccessMutation.mutate()}
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
            <p className="text-sm leading-relaxed text-forest/80">{book.synopsis}</p>
            <div className="flex flex-col gap-3 rounded-xl border border-honey/40 bg-honey/15 p-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm font-semibold text-forest">
                🎨 구독 회원이 되면 이 책의 웹툰 요약 컷을 볼 수 있어요
              </p>
              <button className="shrink-0 rounded-full bg-forest px-5 py-2 text-sm font-semibold text-paper transition hover:bg-forest-light">
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
              placeholder="한줄평을 입력하세요..."
              className="flex-1 rounded-lg border border-forest/20 px-4 py-2.5 outline-none focus:border-forest"
            />
            <button
              onClick={handleSubmitReview}
              className="shrink-0 rounded-lg bg-forest px-6 py-2.5 font-semibold text-paper transition hover:bg-forest-light"
            >
              등록하기
            </button>
          </div>
        </div>

        <div className="flex flex-col gap-3">
          {reviews.map((review) => (
            <div key={review.id} className="rounded-xl bg-paper p-4">
              <p className="text-sm font-semibold">
                {review.author} {"⭐".repeat(review.rating)} | {review.date}
              </p>
              <p className="mt-1 text-sm text-forest/80">{review.text}</p>
            </div>
          ))}
        </div>
      </section>

      <EbookViewer
        isOpen={isEbookOpen}
        onClose={() => setIsEbookOpen(false)}
        url={ebookUrl}
        title={book.title}
        bookId={id}
      />
    </main>
  );
}
