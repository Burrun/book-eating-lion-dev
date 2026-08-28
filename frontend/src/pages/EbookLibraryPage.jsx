import { useEffect, useMemo, useRef, useState } from "react";
import { BookOpen, Quote, Search, Trash2, X } from "lucide-react";
import Skeleton from "../components/Skeleton.jsx";
import EbookViewer from "../components/EbookViewer.jsx";
import { useToast } from "../components/Toast.jsx";
import { getEbookAccess, getMyEbooks } from "../api/books.ts";
import { deleteHighlight, getMyHighlights } from "../api/bookHighlight.ts";
import { readProgress } from "../hooks/useReadingProgress.js";

/**
 * 이북 보관함 — 내가 산 eBook 과 거기 남긴 메모.
 *
 * 마이페이지에서 갈라져 나왔다. 마이페이지는 "계정/주문"이고 여기는 "읽기"라, 한 화면에
 * 같이 두면 스크롤만 길어지고 둘 다 안 보인다. Header 가 /mypage 아래에서 두 곳을 잇는
 * 하위 내비를 그린다.
 *
 * 🔴 진도(%)와 "최근 읽은 순" 정렬은 지금 <b>이 브라우저의 localStorage</b>만 본다.
 * GET /api/catalog/ebooks/me 응답에 진도가 없어서다 — 폰에서 읽은 책은 PC 보관함에서
 * 0%로 보인다. 서버 동기화는 docs/TODOS.md 참고.
 */
export default function EbookLibraryPage() {
  const toast = useToast();

  const [ebooks, setEbooks] = useState(null);
  const [ebooksError, setEbooksError] = useState(false);
  const [openingBookId, setOpeningBookId] = useState(null);
  const [activeBook, setActiveBook] = useState(null); // { id, title } | null
  const [ebookUrl, setEbookUrl] = useState(null);
  // 구매 확정 여부(구독 열람과 구분). 뷰어의 사자 진입점 노출에만 쓴다.
  const [isPurchased, setIsPurchased] = useState(false);

  const [highlights, setHighlights] = useState(null);
  const [highlightsError, setHighlightsError] = useState(false);
  // null 이면 전체. 값이 있으면 그 책의 메모만 본다.
  const [filterBookId, setFilterBookId] = useState(null);
  const [keyword, setKeyword] = useState("");
  const memoSectionRef = useRef(null);

  useEffect(() => {
    let ignore = false;
    getMyEbooks()
      .then((data) => {
        if (!ignore) setEbooks(data);
      })
      .catch(() => {
        if (!ignore) {
          setEbooks([]);
          setEbooksError(true);
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  const loadHighlights = () => {
    getMyHighlights()
      .then(setHighlights)
      .catch(() => {
        setHighlights([]);
        setHighlightsError(true);
      });
  };

  useEffect(loadHighlights, []);

  // 진도는 localStorage 라 뷰어를 닫고 돌아오면 값이 바뀌어 있다. activeBook 이 null 로
  // 돌아오는 순간(=뷰어를 닫은 순간)을 의존성으로 삼아 다시 읽는다.
  const progressByBookId = useMemo(() => {
    if (!ebooks) return {};
    return Object.fromEntries(ebooks.map((book) => [book.id, readProgress(book.id)]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ebooks, activeBook]);

  // 최근 읽은 순. 한 번도 안 읽은 책은 뒤로 보낸다 — 보관함에 들어온 사람이 먼저 찾는 건
  // 읽던 책이다.
  const sortedEbooks = useMemo(() => {
    if (!ebooks) return null;
    return [...ebooks].sort((a, b) => {
      const aAt = Date.parse(progressByBookId[a.id]?.updatedAt ?? "") || 0;
      const bAt = Date.parse(progressByBookId[b.id]?.updatedAt ?? "") || 0;
      return bAt - aAt;
    });
  }, [ebooks, progressByBookId]);

  const memoCountByBookId = useMemo(() => {
    const counts = {};
    for (const highlight of highlights ?? []) {
      counts[highlight.bookId] = (counts[highlight.bookId] ?? 0) + 1;
    }
    return counts;
  }, [highlights]);

  const visibleHighlights = useMemo(() => {
    const trimmed = keyword.trim().toLowerCase();
    return (highlights ?? []).filter((highlight) => {
      if (filterBookId != null && highlight.bookId !== filterBookId) return false;
      if (!trimmed) return true;
      return (
        highlight.selectedText.toLowerCase().includes(trimmed) ||
        (highlight.memoText ?? "").toLowerCase().includes(trimmed) ||
        highlight.bookTitle.toLowerCase().includes(trimmed)
      );
    });
  }, [highlights, filterBookId, keyword]);

  // 칩은 메모가 실제로 있는 책만 그린다. 보관함 전체를 칩으로 깔면 대부분이 0건이라
  // 누를 이유가 없는 버튼이 줄지어 선다.
  const filterChips = useMemo(() => {
    const seen = new Map();
    for (const highlight of highlights ?? []) {
      if (!seen.has(highlight.bookId)) seen.set(highlight.bookId, highlight.bookTitle);
    }
    return [...seen].map(([bookId, bookTitle]) => ({ bookId, bookTitle }));
  }, [highlights]);

  const handleOpen = async (book) => {
    setOpeningBookId(book.id);
    try {
      const access = await getEbookAccess(book.id);
      if (!access.ebookAvailable || !access.presignedUrl) {
        toast.error("아직 eBook이 준비되지 않은 도서입니다.");
        return;
      }
      setEbookUrl(access.presignedUrl);
      setIsPurchased(access.purchased);
      setActiveBook(book);
    } catch (err) {
      toast.error(
        err?.code === "EBOOK_OWNERSHIP_REQUIRED"
          ? "구매 확정된 도서만 열람할 수 있어요."
          : "eBook을 열지 못했습니다. 잠시 후 다시 시도해주세요.",
      );
    } finally {
      setOpeningBookId(null);
    }
  };

  // 뷰어에서 메모를 새로 썼을 수 있으니 닫을 때 목록을 다시 받는다.
  const handleClose = () => {
    setActiveBook(null);
    setEbookUrl(null);
    setIsPurchased(false);
    loadHighlights();
  };

  const handleFilterByBook = (bookId) => {
    setFilterBookId(bookId);
    memoSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  // 목록에서 먼저 지우고 서버를 부른다. 실패하면 되돌린다 — 되돌릴 수 있는 낙관적 갱신이라
  // 확인 모달까지 세우지 않는다.
  const handleDelete = (highlight) => {
    setHighlights((prev) => prev.filter((h) => h.highlightId !== highlight.highlightId));
    deleteHighlight(highlight.highlightId).catch(() => {
      setHighlights((prev) => [highlight, ...prev]);
      toast.error("메모를 삭제하지 못했어요. 잠시 후 다시 시도해주세요.");
    });
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-lg text-[var(--color-forest)]">내 이북 보관함</h1>
        <p className="text-sm text-[var(--color-ink)] opacity-50">최근 읽은 순</p>

        <div className="pt-5">
          {sortedEbooks === null ? (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
              <Skeleton variant="rectangular" className="h-52 w-full" />
              <Skeleton variant="rectangular" className="h-52 w-full" />
            </div>
          ) : ebooksError ? (
            <EmptyState message="이북 보관함을 불러오지 못했습니다. 잠시 후 다시 시도해주세요." />
          ) : sortedEbooks.length === 0 ? (
            <EmptyState message="구매한 eBook이 없어요. eBook이 있는 도서를 구매하면 여기서 바로 읽을 수 있어요." />
          ) : (
            <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
              {sortedEbooks.map((book) => (
                <EbookCard
                  key={book.id}
                  book={book}
                  percentage={progressByBookId[book.id]?.percentage ?? null}
                  memoCount={memoCountByBookId[Number(book.id)] ?? 0}
                  isOpening={openingBookId === book.id}
                  onOpen={() => handleOpen(book)}
                  onShowMemos={() => handleFilterByBook(Number(book.id))}
                />
              ))}
            </ul>
          )}
        </div>
      </section>

      <section
        ref={memoSectionRef}
        className="mt-6 scroll-mt-24 rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-display text-lg text-[var(--color-forest)]">📝 내 메모</h2>
          <label className="flex min-w-56 flex-1 items-center gap-2 rounded-full border border-[var(--color-forest)]/20 px-3.5 py-2 focus-within:border-[var(--color-honey)] sm:max-w-xs sm:flex-none">
            <Search size={15} className="shrink-0 text-[var(--color-forest)]/50" />
            <input
              type="search"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="메모 검색"
              className="w-full min-w-0 bg-transparent text-sm text-[var(--color-ink)] placeholder:text-[var(--color-ink)]/40 focus:outline-none"
            />
          </label>
        </div>

        {filterChips.length > 0 && (
          <div className="mb-4 flex flex-wrap gap-2">
            <FilterChip
              label="전체"
              isActive={filterBookId === null}
              onClick={() => setFilterBookId(null)}
            />
            {filterChips.map((chip) => (
              <FilterChip
                key={chip.bookId}
                label={chip.bookTitle}
                isActive={filterBookId === chip.bookId}
                onClick={() => setFilterBookId(chip.bookId)}
                onClear={filterBookId === chip.bookId ? () => setFilterBookId(null) : undefined}
              />
            ))}
          </div>
        )}

        {highlights === null ? (
          <div className="flex flex-col gap-2">
            <Skeleton variant="rectangular" className="h-16 w-full" />
            <Skeleton variant="rectangular" className="h-16 w-full" />
          </div>
        ) : highlightsError ? (
          <EmptyState message="메모를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요." />
        ) : highlights.length === 0 ? (
          <EmptyState message="아직 메모가 없어요. eBook을 읽다가 문장을 더블클릭하거나 드래그하면 메모를 남길 수 있어요!" />
        ) : visibleHighlights.length === 0 ? (
          <EmptyState message="조건에 맞는 메모가 없어요." />
        ) : (
          <ul className="flex flex-col gap-3">
            {visibleHighlights.map((highlight) => (
              <li
                key={highlight.highlightId}
                className="flex items-start gap-3 rounded-xl border border-[var(--color-forest)]/10 p-4"
              >
                <Quote size={14} className="mt-1 shrink-0 text-[var(--color-forest)]/40" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium text-[var(--color-forest)]">
                    {highlight.bookTitle}
                  </p>
                  <blockquote className="mt-1.5 border-l-2 border-[var(--color-honey)] pl-3 text-sm leading-relaxed text-[var(--color-ink)]/80">
                    {highlight.selectedText}
                  </blockquote>
                  {highlight.memoText && (
                    <p className="mt-2 text-sm whitespace-pre-wrap text-[var(--color-ink)]">
                      {highlight.memoText}
                    </p>
                  )}
                </div>
                <button
                  type="button"
                  aria-label="메모 삭제"
                  onClick={() => handleDelete(highlight)}
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[var(--color-ink)]/30 transition-colors hover:bg-[var(--color-coral)]/10 hover:text-[var(--color-coral)]"
                >
                  <Trash2 size={15} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <EbookViewer
        isOpen={Boolean(activeBook)}
        onClose={handleClose}
        url={ebookUrl}
        title={activeBook?.title}
        bookId={activeBook?.id}
        purchased={isPurchased}
      />
    </div>
  );
}

/**
 * 표지 카드. 진도는 표지 위에 얹는다 — 목록에서 눈이 먼저 가는 것이 표지라서, 진도를 아래
 * 텍스트로 빼면 "어디까지 읽었더라"를 찾으려고 다시 훑게 된다.
 */
function EbookCard({ book, percentage, memoCount, isOpening, onOpen, onShowMemos }) {
  const hasProgress = typeof percentage === "number";

  return (
    <li className="flex flex-col gap-2">
      <button
        type="button"
        onClick={onOpen}
        disabled={isOpening}
        className="group relative aspect-[3/4] w-full overflow-hidden rounded-xl border border-[var(--color-forest)]/10 bg-[var(--color-forest)]/5 transition-shadow hover:shadow-md disabled:opacity-60"
      >
        {book.coverImageUrl ? (
          <img
            src={book.coverImageUrl}
            alt={book.title}
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : (
          <span className="flex h-full w-full items-center justify-center text-[var(--color-forest)]/30">
            <BookOpen size={32} />
          </span>
        )}

        {hasProgress && (
          <>
            <span className="absolute top-2 right-2 rounded-full bg-[var(--color-forest)]/85 px-2 py-0.5 text-[11px] font-bold text-[var(--color-paper)]">
              {percentage}%
            </span>
            <span className="absolute inset-x-0 bottom-0 h-1 bg-[var(--color-forest)]/15">
              <span
                className="block h-full bg-[var(--color-honey)]"
                style={{ width: `${percentage}%` }}
              />
            </span>
          </>
        )}

        <span className="absolute inset-0 flex items-center justify-center bg-[var(--color-forest)]/60 text-sm font-semibold text-[var(--color-paper)] opacity-0 transition-opacity group-hover:opacity-100">
          {isOpening ? "여는 중..." : "📱 읽기"}
        </span>
      </button>

      <p className="line-clamp-2 text-sm font-medium text-[var(--color-ink)]">{book.title}</p>

      {memoCount > 0 && (
        <button
          type="button"
          onClick={onShowMemos}
          className="self-start rounded-full bg-[var(--color-honey)]/20 px-2.5 py-1 text-[11px] font-medium text-[var(--color-forest)] transition-colors hover:bg-[var(--color-honey)]/40"
        >
          메모 {memoCount}개
        </button>
      )}
    </li>
  );
}

function FilterChip({ label, isActive, onClick, onClear }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border text-sm transition-colors ${
        isActive
          ? "border-[var(--color-forest)] bg-[var(--color-forest)] text-[var(--color-paper)]"
          : "border-[var(--color-forest)]/20 text-[var(--color-ink)]/70 hover:border-[var(--color-forest)]/50"
      }`}
    >
      <button
        type="button"
        onClick={onClick}
        aria-pressed={isActive}
        className="max-w-40 truncate py-1.5 pr-2 pl-3.5"
      >
        {label}
      </button>
      {onClear && (
        <button
          type="button"
          aria-label={`${label} 필터 해제`}
          onClick={onClear}
          className="py-1.5 pr-3 pl-1"
        >
          <X size={13} />
        </button>
      )}
    </span>
  );
}

function EmptyState({ message }) {
  return <p className="py-10 text-center text-sm text-[var(--color-ink)] opacity-40">{message}</p>;
}
