import { useCallback, useEffect, useRef, useState } from "react";
import { apiClient, unwrap } from "../api/client.ts";
import { isLoggedIn } from "../api/authStorage.js";

const STORAGE_KEY_PREFIX = "reading-progress:";
const SAVE_DEBOUNCE_MS = 500;
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

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
 * 로그인 사용자는 서버 API를 기준으로 동기화하고 localStorage는 즉시 복원용 캐시로 사용한다.
 * 목업/비로그인 상태에서는 기존처럼 localStorage만 사용한다.
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
    if (!bookId || USE_MOCK || !isLoggedIn()) return;
    let cancelled = false;
    unwrap(apiClient.get(`/catalog/books/${bookId}/reading-progress`))
      .then((serverProgress) => {
        if (cancelled || !serverProgress) return;
        writeProgress(bookId, serverProgress.cfi, serverProgress.percentage);
        setProgress(serverProgress);
      })
      .catch(() => {
        // 네트워크 장애 시 로컬 캐시로 계속 읽을 수 있게 둔다.
      });
    return () => {
      cancelled = true;
    };
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
        setProgress({ cfi, percentage: typeof percentage === "number" ? percentage : null });
        if (!USE_MOCK && isLoggedIn()) {
          unwrap(
            apiClient.put(`/catalog/books/${bookId}/reading-progress`, { cfi, percentage }),
          ).catch(() => {
            // 서버 저장 실패에도 현재 독서 세션과 로컬 복원은 유지한다.
          });
        }
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
