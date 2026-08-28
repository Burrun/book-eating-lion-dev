// GET /api/catalog/members/me/books/feedable, PATCH .../reading-progress/fed 목업.
// 실서버는 reading_progress 를 보지만 목업은 고정 목록에서 먹은 책만 빼는 것으로 흉내 낸다.
import type { FeedableBook } from "../api/readingProgress.ts";

const MOCK_FEEDABLE: FeedableBook[] = [
  { bookId: 101, bookTitle: "Frankenstein", coverImageUrl: null, percentage: 100 },
  {
    bookId: 102,
    bookTitle: "Alice's Adventures in Wonderland",
    coverImageUrl: null,
    percentage: 97,
  },
];

const fedBookIds = new Set<number>();

export function mockGetFeedableBooks(): FeedableBook[] {
  return MOCK_FEEDABLE.filter((book) => !fedBookIds.has(book.bookId));
}

export function mockMarkBookFed(bookId: number | string): void {
  fedBookIds.add(Number(bookId));
}
