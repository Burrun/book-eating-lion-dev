import { useCallback, useEffect, useRef, useState } from "react";
import { getReadingProgress, saveReadingProgress } from "../api/readingProgress.ts";

const STORAGE_KEY_PREFIX = "reading-progress:";
const SAVE_DEBOUNCE_MS = 500;

function readLocalProgress(bookId) {
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

function writeLocalProgress(bookId, cfi, percentage) {
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
 * 전자책 이어읽기 위치(+ 진행률)를 저장/복원한다. 서버 API를 우선으로 쓰고,
 * localStorage는 오프라인/서버 실패 대비 폴백 겸 백업으로 유지한다.
 *
 * - 마운트 시: localStorage 값으로 즉시 시작(동기, 깜빡임 없음) → 서버 조회가 끝나면
 *   기록이 있는 경우에만 그 값으로 갱신한다. 서버가 null(기록 없음)을 주면 로컬 값은
 *   그대로 둔다 — 아직 이 기기 값이 서버에 반영되기 전일 수 있어서 지우지 않는다.
 *   서버 조회 자체가 실패(네트워크/인증 등)해도 조용히 무시하고 로컬 값을 그대로 쓴다.
 * - saveLocation: 500ms debounce 후 localStorage에 먼저 쓰고(항상 성공하는 백업),
 *   이어서 서버 저장을 시도한다. 서버 저장 실패는 조용히 무시한다(로컬엔 이미 남아있음).
 */
export function useReadingProgress(bookId) {
  // lazy init: 마운트 시 동기적으로 읽어 이후 EbookViewer의 open 이펙트가
  // 항상 최신 값을 보도록 한다 (CSR 전용 앱이라 localStorage 동기 접근이 안전함).
  const [progress, setProgress] = useState(() => (bookId ? readLocalProgress(bookId) : null));
  const timeoutRef = useRef(null);
  const mountedBookIdRef = useRef(bookId);

  useEffect(() => {
    // bookId가 최초 마운트 이후 바뀐 경우에만 로컬 값으로 다시 시작한다
    // (최초 값은 lazy init이 이미 처리했다).
    if (mountedBookIdRef.current === bookId) return;
    mountedBookIdRef.current = bookId;
    setProgress(bookId ? readLocalProgress(bookId) : null);
  }, [bookId]);

  useEffect(() => {
    if (!bookId) return undefined;
    let cancelled = false;

    getReadingProgress(bookId)
      .then((serverProgress) => {
        if (cancelled || !serverProgress) return;
        setProgress({ cfi: serverProgress.cfi, percentage: serverProgress.percentage ?? null });
      })
      .catch(() => {
        // 오프라인, 401 등은 조용히 무시 — 이미 로컬 값으로 시작한 상태를 그대로 둔다.
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
        writeLocalProgress(bookId, cfi, percentage);
        saveReadingProgress(bookId, cfi, percentage).catch(() => {
          // 서버 저장 실패(오프라인 등)는 조용히 무시한다 — 로컬 백업은 이미 저장됐다.
        });
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
