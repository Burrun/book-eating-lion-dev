// 리뷰 목업. 다른 목업과 동일하게 백엔드 DTO(ReviewResponse) 형태로 두고,
// UI 타입 변환은 api/reviews.ts 가 매퍼(toReview)로 처리한다.
// 작성자 표시명은 nickname 스냅샷이 있으면 그걸 쓰고, 없으면 user_{memberId}로 만든다(toReview 참고).
//
// 실API와 마찬가지로 작성/수정/삭제가 실제로 반영되도록 모듈 스코프 mutable 배열로 관리한다
// (mocks/cards.ts와 동일 패턴) — 예전엔 조회 전용 고정 배열이라 작성/수정/삭제 자체가 없었다.
import { MOCK_PROFILE } from "./mypage.js";
import type { Page, ReviewRequest, ReviewResponse, ReviewUpdateRequest } from "../api/types.ts";

let reviews: ReviewResponse[] = [
  {
    id: 1,
    bookId: 1,
    memberId: "102",
    nickname: "책벌레",
    rating: 5,
    content: "JPA 입문에 최고의 책입니다. 웹툰 요약 컷도 쉽게 잘 정리되어 있어서 대만족!",
    createdAt: "2026-07-29T10:12:00",
  },
  {
    id: 2,
    bookId: 2,
    memberId: "88",
    nickname: "개발자",
    rating: 4,
    content: "개발자 필독서! 명구절들 정리해두기 좋았습니다.",
    createdAt: "2026-07-25T09:40:00",
  },
  {
    id: 3,
    bookId: 3,
    memberId: "45",
    nickname: "독서가",
    rating: 5,
    content: "저렴하게 잘 구해서 읽었습니다.",
    createdAt: "2026-07-18T20:05:00",
  },
];

let nextReviewId = 4;

export function mockGetReviews(bookId: number | string, page = 0, size = 20): Page<ReviewResponse> {
  const matched = reviews.filter((review) => String(review.bookId) === String(bookId));
  const totalPages = Math.max(1, Math.ceil(matched.length / size));
  const number = Math.min(Math.max(page, 0), totalPages - 1);
  return {
    content: matched.slice(number * size, number * size + size),
    number,
    size,
    totalElements: matched.length,
    totalPages,
    first: number === 0,
    last: number === totalPages - 1,
  };
}

export function mockCreateReview(bookId: number | string, body: ReviewRequest): ReviewResponse {
  const review: ReviewResponse = {
    id: nextReviewId++,
    bookId: Number(bookId),
    memberId: MOCK_PROFILE.id,
    nickname: "나",
    rating: body.rating,
    content: body.content,
    createdAt: new Date().toISOString(),
  };
  reviews = [review, ...reviews];
  return review;
}

export function mockUpdateReview(
  reviewId: number | string,
  body: ReviewUpdateRequest,
): ReviewResponse | undefined {
  let updated: ReviewResponse | undefined;
  reviews = reviews.map((review) => {
    if (String(review.id) !== String(reviewId)) return review;
    updated = { ...review, rating: body.rating, content: body.content };
    return updated;
  });
  return updated;
}

export function mockDeleteReview(reviewId: number | string): void {
  reviews = reviews.filter((review) => String(review.id) !== String(reviewId));
}
