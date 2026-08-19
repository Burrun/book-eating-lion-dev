import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getAdminBookReviews } from "../../api/admin/reviews.ts";

const PAGE_SIZE = 20;

export default function AdminReviewsPage() {
  const [bookIdInput, setBookIdInput] = useState("");
  const [bookId, setBookId] = useState("");
  const [page, setPage] = useState(0);

  const {
    data: reviewPage,
    isPending,
    isError,
  } = useQuery({
    queryKey: ["admin", "reviews", bookId, page],
    queryFn: () => getAdminBookReviews(bookId, { page, size: PAGE_SIZE }),
    enabled: Boolean(bookId),
  });

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">⭐ 리뷰 조회</h1>
        <p className="mb-4 text-sm text-[var(--color-ink)] opacity-60">
          도서 ID로 검색해 해당 도서의 리뷰를 열람합니다. (조회 전용 — 삭제/숨김 API는 아직
          없습니다)
        </p>

        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            setBookId(bookIdInput.trim());
          }}
        >
          <input
            type="text"
            value={bookIdInput}
            onChange={(e) => setBookIdInput(e.target.value)}
            placeholder="도서 ID"
            className="w-full max-w-xs rounded-lg border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
          />
          <button
            type="submit"
            className="rounded-lg border-2 border-[var(--color-forest)] px-4 py-2.5 text-sm font-semibold text-[var(--color-forest)] transition hover:bg-[var(--color-forest)] hover:text-[var(--color-paper)]"
          >
            검색
          </button>
        </form>
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        {!bookId ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            도서 ID를 입력해 검색해 주세요.
          </p>
        ) : isPending ? (
          <div className="flex flex-col gap-3">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer h-20 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-[var(--color-coral)]">
            리뷰 목록을 불러오지 못했습니다.
          </p>
        ) : !reviewPage || reviewPage.content.length === 0 ? (
          <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-60">
            등록된 리뷰가 없습니다.
          </p>
        ) : (
          <>
            <ul className="flex flex-col gap-3">
              {reviewPage.content.map((review) => (
                <li
                  key={review.id}
                  className="flex flex-col gap-1 rounded-xl border border-[var(--color-forest)]/15 bg-white p-4"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-[var(--color-ink)]">
                      {review.nickname ?? `user_${review.memberId}`}
                    </span>
                    <span className="text-sm text-[var(--color-honey)]">
                      {"★".repeat(review.rating)}
                    </span>
                  </div>
                  <p className="text-sm text-[var(--color-ink)] opacity-70">{review.content}</p>
                  <p className="text-xs text-[var(--color-ink)] opacity-40">{review.createdAt}</p>
                </li>
              ))}
            </ul>

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
                {reviewPage.number + 1} / {reviewPage.totalPages}
              </span>
              <button
                type="button"
                disabled={reviewPage.last}
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
