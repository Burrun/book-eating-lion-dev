// POST/GET/DELETE 하이라이트 메모 목업. cards.ts/addresses.ts와 같은 패턴 —
// 모듈 스코프 mutable 값으로 상태를 유지한다.
import type { BookHighlight, BookHighlightInput } from "../api/bookHighlight.ts";

const MOCK_BOOK_TITLES: Record<string, string> = {
  "101": "Frankenstein",
  "102": "Alice's Adventures in Wonderland",
};

let nextId = 1;
const highlights: BookHighlight[] = [];

export function mockCreateHighlight(
  bookId: number | string,
  input: BookHighlightInput,
): BookHighlight {
  const highlight: BookHighlight = {
    highlightId: nextId++,
    bookId: Number(bookId),
    bookTitle: MOCK_BOOK_TITLES[String(bookId)] ?? `도서 #${bookId}`,
    cfiRange: input.cfiRange,
    selectedText: input.selectedText,
    memoText: input.memoText || null,
    createdAt: new Date().toISOString(),
  };
  highlights.unshift(highlight);
  return highlight;
}

export function mockGetMyHighlights(): BookHighlight[] {
  return [...highlights];
}

export function mockDeleteHighlight(highlightId: number): void {
  const index = highlights.findIndex((h) => h.highlightId === highlightId);
  if (index >= 0) highlights.splice(index, 1);
}
