import { useCallback, useEffect, useRef, useState } from "react";

const STORAGE_KEY_PREFIX = "reading-progress:";
const SAVE_DEBOUNCE_MS = 500;

function readProgress(bookId) {
  try {
    const raw = localStorage.getItem(`${STORAGE_KEY_PREFIX}${bookId}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (typeof parsed?.cfi !== "string") return null;
    // percentage는 구버전 데이터엔 없을 수 있다 (optional).
    const percentage = typeof parsed?.percentage === "number" ? parsed.percentage : null;
    return { cfi: parsed.cfi, percentage };
  } catch {
    return null;
  }
}

function writeProgress(bookId, cfi, percentage) {
  try {
    localStorage.setItem(
      `${STORAGE_KEY_PREFIX}${bookId}`,
      JSON.stringify({
        cfi,
        percentage: typeof percentage === "number" ? percentage : null,
        updatedAt: new Date().toISOString(),
      }),
    );
  } catch {
    // 용량 초과, 파싱 에러, 프라이빗 모드 등은 조용히 무시한다.
  }
}

/**
 * 전자책 이어읽기 위치(+ 진행률)를 저장/복원한다.
 * 지금은 localStorage에만 저장하지만, 서버 API가 준비되면
 * readProgress/writeProgress 내부만 API 호출로 바꾸면 된다 (인터페이스는 유지).
 */
export function useReadingProgress(bookId) {
  // lazy init: 마운트 시 동기적으로 읽어 이후 EbookViewer의 open 이펙트가
  // 항상 최신 값을 보도록 한다 (CSR 전용 앱이라 localStorage 동기 접근이 안전함).
  const [progress, setProgress] = useState(() => (bookId ? readProgress(bookId) : null));
  const timeoutRef = useRef(null);
  const mountedBookIdRef = useRef(bookId);

  useEffect(() => {
    // bookId가 최초 마운트 이후 바뀐 경우에만 다시 읽는다 (최초 값은 lazy init이 이미 처리함).
    if (mountedBookIdRef.current === bookId) return;
    mountedBookIdRef.current = bookId;
    setProgress(bookId ? readProgress(bookId) : null);
  }, [bookId]);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [bookId]);

  const saveLocation = useCallback(
    (cfi, percentage) => {
      if (!bookId || !cfi) return;
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      timeoutRef.current = setTimeout(() => {
        writeProgress(bookId, cfi, percentage);
      }, SAVE_DEBOUNCE_MS);
    },
    [bookId],
  );

  return {
    initialCfi: progress?.cfi ?? null,
    initialPercentage: progress?.percentage ?? null,
    saveLocation,
  };
}
