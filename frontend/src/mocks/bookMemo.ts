// GET/PUT /api/catalog/books/{bookId}/memo, GET /api/catalog/members/me/memos/feedable,
// PATCH /api/catalog/books/{bookId}/memo/fed 목업. cards.ts/addresses.ts와 같은 패턴 —
// 모듈 스코프 mutable 값으로 상태를 유지한다.
import type { BookMemo, FeedableMemo, FedMemo } from "../api/bookMemo.ts";

const MOCK_BOOK_TITLES: Record<string, string> = {
  "101": "Frankenstein",
  "102": "Alice's Adventures in Wonderland",
};

const memos = new Map<string, BookMemo>();

export function mockGetBookMemo(bookId: string): BookMemo | null {
  return memos.get(bookId) ?? null;
}

export function mockSaveBookMemo(bookId: string, memoText: string): BookMemo {
  const existing = memos.get(bookId);
  const memo: BookMemo = {
    bookId,
    memoText,
    fedAt: existing?.fedAt ?? null,
    updatedAt: new Date().toISOString(),
  };
  memos.set(bookId, memo);
  return memo;
}

export function mockGetFeedableMemos(): FeedableMemo[] {
  return [...memos.values()]
    .filter((memo) => !memo.fedAt)
    .map((memo) => ({
      bookId: memo.bookId,
      bookTitle: MOCK_BOOK_TITLES[memo.bookId] ?? `도서 #${memo.bookId}`,
      memoText: memo.memoText,
    }));
}

export function mockGetFedMemos(): FedMemo[] {
  return [...memos.values()]
    .filter((memo) => Boolean(memo.fedAt))
    .map((memo) => ({
      bookId: memo.bookId,
      bookTitle: MOCK_BOOK_TITLES[memo.bookId] ?? `도서 #${memo.bookId}`,
      memoText: memo.memoText,
    }));
}

export function mockMarkMemoFed(bookId: string): void {
  const existing = memos.get(bookId);
  if (existing) {
    memos.set(bookId, { ...existing, fedAt: new Date().toISOString() });
  }
}
