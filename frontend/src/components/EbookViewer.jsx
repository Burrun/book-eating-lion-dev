import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ReactReader } from "react-reader";
import { X } from "lucide-react";
import { useReadingProgress } from "../hooks/useReadingProgress.js";
import ErrorBoundary from "./ErrorBoundary.jsx";

const LOCATIONS_KEY_PREFIX = "locations:";

function safeGetItem(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSetItem(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // 용량 초과 등은 조용히 무시한다. 캐시가 없으면 다음 방문 때 다시 generate()할 뿐이다.
  }
}

/**
 * 전자책(EPUB) 전체화면 뷰어.
 *
 * `url`은 로컬 정적 경로(/ebooks/xxx.epub)든 S3 presigned URL이든 그대로 넘기면 된다.
 * 실 API(GET /api/catalog/books/{bookId}/ebook) 연동 시에도 이 컴포넌트는 그대로 두고
 * 호출부(ProductDetailPage)에서 응답으로 받은 URL을 넘기기만 하면 된다.
 */
export default function EbookViewer({ isOpen, onClose, url, title, bookId, onProgressChange }) {
  const { initialCfi, saveLocation } = useReadingProgress(bookId);
  // 저장된 이어읽기 위치(없으면 처음부터)로 시작한다. 이후로는 locationChanged가 갱신한다.
  const [location, setLocation] = useState(initialCfi);
  const [isIndexing, setIsIndexing] = useState(false);
  const epubBookRef = useRef(null);
  const initialCfiBookRef = useRef(null);
  const lastAppliedInitialCfiRef = useRef(null);
  // 읽는 도중 다른 기기의 서버 진행률 조회가 뒤늦게 도착해도, 사용자가 이미 이 세션에서
  // 페이지를 넘겼다면 위치를 되돌리지 않는다 — 안 그러면 한창 읽던 위치가 초기 위치로 튄다.
  const hasUserNavigatedRef = useRef(false);
  // bookId가 바뀌면 이전 책의 위치를 리렌더 중에 즉시 리셋한다 — state(ref 아님) 비교로
  // 하는 React 공식 "렌더 중 조정" 패턴이라 refs-in-render/set-state-in-effect 둘 다 안 걸린다.
  const [renderedBookId, setRenderedBookId] = useState(bookId);
  if (renderedBookId !== bookId) {
    setRenderedBookId(bookId);
    setLocation(null);
  }

  // ref 리셋은 렌더 중이 아니라 이펙트에서 한다 — refs-in-render 규칙은 렌더 함수 본문에서의
  // ref 접근만 막고, 이펙트 안에서의 ref 갱신은 원래도 허용된다.
  useEffect(() => {
    hasUserNavigatedRef.current = false;
    initialCfiBookRef.current = null;
    lastAppliedInitialCfiRef.current = null;
  }, [bookId]);

  useEffect(() => {
    if (hasUserNavigatedRef.current || !initialCfi) return;
    if (initialCfiBookRef.current === bookId && lastAppliedInitialCfiRef.current === initialCfi) {
      return;
    }
    setLocation(initialCfi);
    initialCfiBookRef.current = bookId;
    lastAppliedInitialCfiRef.current = initialCfi;
  }, [bookId, initialCfi]);

  // react-reader/epub.js locations(=페이지 인덱스)를 진행률 계산에 쓴다. 책마다 한 번만
  // generate()하면 되므로 localStorage에 캐싱해서 재방문 시 load()로 복원한다.
  const handleGetRendition = (rendition) => {
    const book = rendition.book;
    epubBookRef.current = book;
    const cacheKey = bookId ? `${LOCATIONS_KEY_PREFIX}${bookId}` : null;

    book.ready.then(async () => {
      const cached = cacheKey ? safeGetItem(cacheKey) : null;
      if (cached) {
        try {
          book.locations.load(cached);
          return;
        } catch {
          // 캐시가 손상된 경우 재생성한다.
        }
      }

      setIsIndexing(true);
      const start = performance.now();
      await book.locations.generate();
      const elapsedMs = Math.round(performance.now() - start);
      console.log(
        `[EbookViewer] locations.generate() took ${elapsedMs}ms (bookId=${bookId ?? "?"})`,
      );
      setIsIndexing(false);

      if (cacheKey) safeSetItem(cacheKey, book.locations.save());
    });

    // react-reader의 locationChanged prop은 epub.js "locationChanged" 이벤트를 거치며 현재
    // 페이지의 "시작 지점" CFI/퍼센트만 넘겨준다 — 끝까지 다 읽어도 마지막 페이지의 시작
    // 지점은 항상 책의 진짜 끝보다 앞이라 100%가 절대 안 찍히는 구조적 한계가 있다(그래서
    // 98%에서 멈춰 보였다). raw rendition의 "relocated" 이벤트는 페이지 끝(end) 기준 퍼센트와
    // "스파인 마지막 + 그 안에서도 마지막 페이지"를 뜻하는 atEnd 플래그를 함께 주므로, 진행률/
    // 완독 판정은 이 이벤트를 직접 구독해서 처리한다.
    rendition.on("relocated", (located) => {
      const cfi = located?.start?.cfi;
      if (!cfi) return;
      hasUserNavigatedRef.current = true;
      const atEnd = Boolean(located.atEnd);
      const rawPercentage = located.end?.percentage ?? located.start?.percentage;
      const percentage = atEnd
        ? 100
        : typeof rawPercentage === "number"
          ? Math.round(rawPercentage * 100)
          : undefined;
      saveLocation(cfi, percentage);
      // saveLocation의 로컬 저장은 500ms 디바운스라, 뷰어를 닫는 시점엔 아직 반영 전일 수
      // 있다 — ProductDetailPage가 useReadingProgress를 별도 인스턴스로 호출해 쓰는 상태라
      // (훅 인스턴스가 달라 서로 리렌더를 안 일으킴) 디바운스와 무관하게 부모에 즉시 알려
      // 완독 요약 메모 UI가 새로고침 없이 바로 뜨게 한다.
      if (typeof percentage === "number") onProgressChange?.(percentage);
    });
  };

  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => e.key === "Escape" && onClose?.();
    document.addEventListener("keydown", handleKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [isOpen, onClose]);

  if (!isOpen || !url) return null;

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title ? `${title} 전자책 뷰어` : "전자책 뷰어"}
      className="fixed inset-0 z-[100] flex flex-col bg-[var(--color-paper)]"
    >
      <div className="flex shrink-0 items-center justify-between border-b border-[var(--color-forest)]/10 bg-white px-4 py-3 sm:px-6">
        <div className="flex min-w-0 items-baseline gap-3">
          <h2 className="line-clamp-1 font-display text-base text-[var(--color-forest)] sm:text-lg">
            {title ?? "전자책 뷰어"}
          </h2>
          {isIndexing && (
            <span className="shrink-0 text-xs text-[var(--color-forest)]/50">진행률 계산 중…</span>
          )}
        </div>
        <button
          type="button"
          aria-label="닫기"
          onClick={() => onClose?.()}
          className="ml-4 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[var(--color-forest)]/60 transition-colors hover:bg-[var(--color-forest)]/10 hover:text-[var(--color-forest)]"
        >
          <X size={20} />
        </button>
      </div>

      {/* react-reader는 root에 height:100%를 쓰므로, 부모가 flex 레이아웃 안에서
          정의된 높이를 갖도록 relative + flex-1 + min-h-0을 함께 둔다. */}
      <div className="relative min-h-0 flex-1">
        <ErrorBoundary
          fallback={
            <div className="flex h-full flex-col items-center justify-center gap-2 p-10 text-center">
              <p className="text-sm text-[var(--color-ink)] opacity-60">
                이 페이지를 표시하는 중 오류가 발생했어요. 뷰어를 닫고 다시 열어주세요.
              </p>
            </div>
          }
        >
          <ReactReader
            url={url}
            title={title}
            location={location}
            locationChanged={(cfi) => {
              // 위치(북마크) 동기화만 담당한다 — 진행률/완독 판정은 handleGetRendition이
              // 구독한 "relocated" 이벤트(더 정확한 end 퍼센트 + atEnd)가 처리한다.
              hasUserNavigatedRef.current = true;
              setLocation(cfi);
            }}
            getRendition={handleGetRendition}
            showToc
          />
        </ErrorBoundary>
      </div>
    </div>,
    document.body,
  );
}
