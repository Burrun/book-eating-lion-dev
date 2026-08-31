import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ReactReader } from "react-reader";
import { X } from "lucide-react";
import { useReadingProgress } from "../hooks/useReadingProgress.js";
import { createHighlight } from "../api/bookHighlight.ts";
import { MAX_SELECTED_CHARS } from "../constants/highlight.ts";
import ErrorBoundary from "./ErrorBoundary.jsx";
import HighlightComposer from "./HighlightComposer.jsx";
import LionAskPanel from "./LionAskPanel.jsx";
import { useToast } from "./Toast.jsx";

const LOCATIONS_KEY_PREFIX = "locations:";

// 드래그 선택은 mouseup으로 잡는데, 더블클릭도 mouseup을 두 번 거친다. 이 시간만큼 미뤘다가
// dblclick이 안 오면 그때 드래그로 확정한다 — 브라우저의 더블클릭 판정 간격과 같은 수준이다.
const SELECTION_SETTLE_MS = 200;

// 문장 끝으로 볼 문자. 한국어 본문에도 영문 마침표가 그대로 쓰여 라틴/전각을 함께 본다.
const SENTENCE_END = /[.!?。！？]/;

/**
 * 더블클릭이 만든 "단어" 선택을 그 단어가 속한 문장으로 넓힌다.
 *
 * 브라우저의 더블클릭은 단어까지만 선택한다. 요구사항은 문장 단위라 앞뒤로 문장 부호를
 * 만날 때까지 훑어 범위를 다시 만든다. 선택이 텍스트 노드 하나 안에 있을 때만 한다 —
 * 노드를 넘나드는 경우(문장 중간에 <em>이 낀 경우 등)는 원래 선택을 그대로 둔다. 드물고,
 * 잘못 넓히면 엉뚱한 곳까지 긁히기 때문이다.
 */
function expandToSentence(range) {
  const node = range.startContainer;
  if (node !== range.endContainer || node.nodeType !== 3) return range;

  const text = node.textContent ?? "";
  let start = range.startOffset;
  let end = range.endOffset;

  while (start > 0 && !SENTENCE_END.test(text[start - 1])) start -= 1;
  while (start < end && /\s/.test(text[start])) start += 1;
  while (end < text.length && !SENTENCE_END.test(text[end])) end += 1;
  if (end < text.length) end += 1;

  const expanded = node.ownerDocument.createRange();
  expanded.setStart(node, start);
  expanded.setEnd(node, end);
  return expanded;
}

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
export default function EbookViewer({
  isOpen,
  onClose,
  url,
  title,
  bookId,
  purchased,
  onProgressChange,
}) {
  const { initialCfi, saveLocation } = useReadingProgress(bookId);
  const toast = useToast();
  // 긁은 문장 + 그 위치. null 이면 작성 패널을 안 띄운다.
  const [draft, setDraft] = useState(null);
  // 사자에게 묻기 패널. 열람 중인 이 책 하나로만 검색을 좁힌다.
  const [isAskOpen, setIsAskOpen] = useState(false);
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
    setDraft(null);
    setIsAskOpen(false);
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

    // 본문은 iframe 안이라 바깥 문서의 이벤트로는 선택을 잡을 수 없다. 렌더된 문서마다
    // 직접 리스너를 단다(react-reader가 챕터를 새로 그릴 때마다 이 훅이 다시 불린다).
    rendition.hooks.content.register((contents) => {
      // 구텐베르크 EPUB의 0.css는 a:hover{color:red}를 둔다 — 진짜 하이퍼링크용이다.
      // 그런데 본문의 북마크용 앵커(<a id="chapNN"/>)는 epub.js가 iframe.srcdoc으로
      // 넣는 순간 text/html로 파싱돼(선언은 application/xhtml+xml인데도) 안 닫힌 <a>가
      // 되고, HTML 파서가 뒤따르는 문단마다 그 <a>를 복제해 다시 연다. 그래서 본문
      // 전체가 hover 시 빨개진다. href 없는 앵커는 링크가 아니므로 hover 색을 뺀다.
      contents.addStylesheetRules([["a:not([href]):hover", ["color", "inherit", true]]]);

      let settleTimer = null;

      const capture = () => {
        const selection = contents.window.getSelection();
        if (!selection || selection.isCollapsed || selection.rangeCount === 0) return;

        const range = selection.getRangeAt(0);
        const text = selection.toString().trim();
        if (!text) return;

        // 🔴 넘치면 잘라서 저장하지 않는다. 조용히 자르면 사용자는 자기가 긁은 문장이
        // 어디서 끊겼는지 모른 채 반쪽짜리 인용을 갖게 된다.
        if (text.length > MAX_SELECTED_CHARS) {
          toast.error(
            `한 번에 ${MAX_SELECTED_CHARS}자까지 저장할 수 있어요 (선택 ${text.length}자).`,
          );
          return;
        }

        setDraft({ cfiRange: contents.cfiFromRange(range), selectedText: text });
      };

      contents.document.addEventListener("mouseup", () => {
        clearTimeout(settleTimer);
        settleTimer = setTimeout(capture, SELECTION_SETTLE_MS);
      });

      contents.document.addEventListener("dblclick", () => {
        clearTimeout(settleTimer);
        const selection = contents.window.getSelection();
        if (!selection || selection.isCollapsed || selection.rangeCount === 0) return;
        const expanded = expandToSentence(selection.getRangeAt(0));
        selection.removeAllRanges();
        selection.addRange(expanded);
        capture();
      });
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

  const handleSaveHighlight = async (memoText) => {
    try {
      await createHighlight(bookId, { ...draft, memoText });
      setDraft(null);
      toast.success("메모를 저장했어요. 마이페이지 '내 메모'에서 볼 수 있어요.");
    } catch {
      toast.error("메모를 저장하지 못했어요. 잠시 후 다시 시도해주세요.");
    }
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
          {isIndexing ? (
            <span className="shrink-0 text-xs text-[var(--color-forest)]/50">진행률 계산 중…</span>
          ) : (
            <span className="hidden shrink-0 text-xs text-[var(--color-forest)]/50 sm:inline">
              문장을 더블클릭하거나 드래그하면 메모를 남길 수 있어요
            </span>
          )}
        </div>
        {/* 🔴 purchased 가 아니면 아예 안 보여준다. 열람은 구독으로도 되지만 사자 RAG의 검색
            권한은 구매 이벤트(ai_db.purchased_books)만 근거로 삼아, 구독 열람 중에 물으면
            "구매한 책에서 근거를 찾지 못했습니다"만 돌아온다 — 눌러도 안 되는 버튼을 두느니
            숨긴다. */}
        {purchased && (
          <button
            type="button"
            aria-label={isAskOpen ? "사자에게 묻기 닫기" : "사자에게 묻기"}
            aria-pressed={isAskOpen}
            onClick={() => setIsAskOpen((open) => !open)}
            className={`ml-auto mr-1 flex h-9 shrink-0 items-center gap-1.5 rounded-full px-3 text-sm transition-colors ${
              isAskOpen
                ? "bg-[var(--color-forest)]/10 text-[var(--color-forest)]"
                : "text-[var(--color-forest)]/60 hover:bg-[var(--color-forest)]/10 hover:text-[var(--color-forest)]"
            }`}
          >
            <span className="text-base leading-none">🦁</span>
            <span className="hidden sm:inline">사자에게 묻기</span>
          </button>
        )}
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

      {isAskOpen && !draft && (
        <div className="shrink-0 border-t border-[var(--color-forest)]/15 bg-white px-4 py-3 sm:px-6">
          <div className="mx-auto max-w-3xl">
            {/* bookIds 를 이 책으로 고정한다 — 읽는 중인 책이 곧 검색 범위라 고를 UI가 없다. */}
            <LionAskPanel bookIds={[bookId]} placeholder="이 책에서 궁금한 걸 물어보세요" />
          </div>
        </div>
      )}

      {draft && (
        <HighlightComposer
          selectedText={draft.selectedText}
          onSave={handleSaveHighlight}
          onCancel={() => setDraft(null)}
        />
      )}
    </div>,
    document.body,
  );
}
