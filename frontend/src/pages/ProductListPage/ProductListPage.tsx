import { useNavigate, useSearchParams } from "react-router-dom";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import BookCard from "../../components/BookCard/BookCard.tsx";
import RecommendationPanel from "../../components/SwipeDeck/RecommendationPanel.tsx";
import { getBooks, searchBooks } from "../../api/books.ts";
import { getCategories } from "../../api/categories.ts";
import { getMySubscription } from "../../api/member.ts";
import { subscriptionCheckoutItem } from "../../constants/subscription.ts";

const PAGE_SIZE = 8;

export default function ProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get("q")?.trim() ?? "";
  const category = searchParams.get("category") ?? "";
  // 전자책은 장르(category)와 다른 축의 필터라 별도 파라미터로 관리한다 — 둘은 배타적이다.
  const isEbookFilter = searchParams.get("ebook") === "1";
  const rawPage = Number(searchParams.get("page") ?? "0");
  const page = Number.isFinite(rawPage) && rawPage >= 0 ? rawPage : 0;

  const navigate = useNavigate();

  const { data: categories = [] } = useQuery({
    queryKey: ["catalog-categories"],
    queryFn: getCategories,
  });

  // ProductDetailPage.tsx의 웹툰 구독 CTA와 같은 패턴.
  const { data: subscription } = useQuery({
    queryKey: ["mySubscription"],
    queryFn: getMySubscription,
  });
  const isSubscribed = subscription?.isActive ?? false;

  // 구독은 결제를 거친다 — 여기서 만들지 않고 /checkout 으로 보낸다.
  // 결제가 확정되면 order-service 가 구독을 활성화한다(constants/subscription.ts 참고).
  const goToSubscriptionCheckout = () =>
    navigate("/checkout", { state: { items: [subscriptionCheckoutItem()] } });

  // 검색어가 있으면 검색 API, 없으면 목록 API를 쓴다.
  const { data, isPending, isError } = useQuery({
    queryKey: ["books", { query, category, isEbookFilter, page }],
    queryFn: () =>
      query
        ? searchBooks({ q: query, page, size: PAGE_SIZE })
        : isEbookFilter
          ? getBooks({ hasEbook: true, page, size: PAGE_SIZE })
          : // 빈 문자열을 보내면 백엔드가 카테고리 필터로 인식하므로 undefined로 넘긴다.
            getBooks({ category: category || undefined, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const books = data?.items ?? [];
  const totalPages = data?.totalPages ?? 1;
  const currentPage = data?.page ?? page;

  function handleCategorySelect(value: string) {
    const next = new URLSearchParams(searchParams);
    if (value) next.set("category", value);
    else next.delete("category");
    next.delete("ebook");
    next.delete("q");
    next.delete("page");
    setSearchParams(next);
  }

  function handleEbookFilterToggle() {
    const next = new URLSearchParams(searchParams);
    if (isEbookFilter) {
      next.delete("ebook");
    } else {
      next.set("ebook", "1");
      next.delete("category");
    }
    next.delete("q");
    next.delete("page");
    setSearchParams(next);
  }

  function clearSearch() {
    const next = new URLSearchParams(searchParams);
    next.delete("q");
    next.delete("page");
    setSearchParams(next);
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams);
    if (nextPage > 0) next.set("page", String(nextPage));
    else next.delete("page");
    setSearchParams(next);
  }

  return (
    <>
      <div className="hidden lg:block fixed right-4 top-24 z-30 w-72">
        <RecommendationPanel />
      </div>

      <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
        <section className="flex flex-col gap-2 rounded-2xl border border-honey/40 bg-honey/15 p-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="font-semibold text-forest">
            🎁 정기 구독: 월 9,900원에 웹툰 요약 컷 열람 &amp; 사자 먹이 2배 적립!
          </p>
          <button
            onClick={goToSubscriptionCheckout}
            disabled={isSubscribed}
            className="shrink-0 rounded-full bg-forest px-5 py-2.5 font-semibold text-paper transition hover:bg-forest-light disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isSubscribed ? "구독 중" : "구독하기 >"}
          </button>
        </section>

        <section
          id="categories"
          className="flex scroll-mt-28 flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6"
        >
          {query ? (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="font-semibold text-forest">
                🔍 &lsquo;{query}&rsquo; 검색 결과
                {data ? <span className="text-forest/60"> {data.totalElements}건</span> : null}
              </p>
              <button
                onClick={clearSearch}
                className="rounded-full border border-forest/15 px-3.5 py-1.5 text-sm font-medium text-forest transition-colors hover:bg-forest/5"
              >
                검색 해제
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              {["전체", ...categories.map((item) => item.categoryName)].map((c) => {
                const value = c === "전체" ? "" : c;
                const active = !isEbookFilter && category === value;
                return (
                  <button
                    key={c}
                    onClick={() => handleCategorySelect(value)}
                    className={`rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors ${
                      active
                        ? "border-coral bg-coral text-white"
                        : "border-forest/15 bg-paper text-forest hover:bg-forest/5"
                    }`}
                  >
                    {c}
                  </button>
                );
              })}
              {/* 장르(category)와는 다른 축의 필터라 별도 파라미터(ebook)로 배타적으로 동작한다. */}
              <button
                onClick={handleEbookFilterToggle}
                className={`rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors ${
                  isEbookFilter
                    ? "border-coral bg-coral text-white"
                    : "border-forest/15 bg-paper text-forest hover:bg-forest/5"
                }`}
              >
                전자책
              </button>
            </div>
          )}

          {isPending ? (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              {Array.from({ length: PAGE_SIZE }, (_, i) => (
                <div key={i} className="skeleton-shimmer h-56 rounded-xl" />
              ))}
            </div>
          ) : isError ? (
            <p className="py-10 text-center text-sm text-coral">
              도서 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
            </p>
          ) : books.length === 0 ? (
            <p className="text-sm text-forest/60">
              {query
                ? "검색 결과가 없습니다"
                : isEbookFilter
                  ? "전자책으로 볼 수 있는 도서가 없습니다"
                  : "해당 카테고리에 도서가 없습니다"}
            </p>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                {books.map((book) => (
                  <BookCard key={book.id} {...book} />
                ))}
              </div>

              {totalPages > 1 && (
                <div className="mt-2 flex items-center justify-center gap-4 text-sm">
                  <button
                    onClick={() => goToPage(Math.max(0, currentPage - 1))}
                    disabled={currentPage <= 0}
                    className="rounded border border-forest/20 px-3 py-1.5 text-forest disabled:opacity-40"
                  >
                    이전
                  </button>
                  <span className="text-forest/60">
                    {currentPage + 1} / {totalPages} 페이지
                  </span>
                  <button
                    onClick={() => goToPage(Math.min(totalPages - 1, currentPage + 1))}
                    disabled={currentPage >= totalPages - 1}
                    className="rounded border border-forest/20 px-3 py-1.5 text-forest disabled:opacity-40"
                  >
                    다음
                  </button>
                </div>
              )}
            </>
          )}
        </section>
      </main>
    </>
  );
}
