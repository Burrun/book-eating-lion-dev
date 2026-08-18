import { swipeDeck } from "./books.ts";
import type { RecommendationQueueResponse, RecommendationReactionRequest } from "../api/types.ts";

const QUEUE_ID = "11111111-1111-4111-8111-111111111111";
let remaining = swipeDeck.map((book, index) => ({
  bookId: Number(book.id),
  title: book.title,
  author: "추천 작가",
  category: "추천",
  price: 15000 + index * 1000,
  coverImageUrl: null,
  score: Math.max(0.5, 0.95 - index * 0.08),
  recommendationReason: book.reason.replace("AI 추천사유: ", ""),
}));

export function mockGetRecommendationQueue(refresh: boolean): RecommendationQueueResponse {
  if (refresh || remaining.length === 0) {
    remaining = swipeDeck.map((book, index) => ({
      bookId: Number(book.id),
      title: book.title,
      author: "추천 작가",
      category: "추천",
      price: 15000 + index * 1000,
      coverImageUrl: null,
      score: Math.max(0.5, 0.95 - index * 0.08),
      recommendationReason: book.reason.replace("AI 추천사유: ", ""),
    }));
  }
  return { queueId: QUEUE_ID, cards: [...remaining] };
}

export function mockReactRecommendation(request: RecommendationReactionRequest): void {
  if (request.queueId !== QUEUE_ID || !remaining.some((book) => book.bookId === request.bookId)) {
    throw new Error("현재 추천 대기열에 없는 도서입니다.");
  }
  remaining = remaining.filter((book) => book.bookId !== request.bookId);
}
