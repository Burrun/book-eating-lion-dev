// 리뷰 목업. 다른 목업과 동일하게 백엔드 DTO(ReviewResponse) 형태로 두고,
// UI 타입 변환은 api/reviews.ts 가 매퍼(toReview)로 처리한다.
// 작성자 표시명은 백엔드가 memberId만 주므로 매퍼가 user_{memberId} 로 만든다.
import type { Page, ReviewResponse } from "../api/types.ts";

const REVIEWS: ReviewResponse[] = [
  {
    id: 1,
    bookId: 1,
    memberId: 102,
    rating: 5,
    content: "JPA 입문에 최고의 책입니다. 웹툰 요약 컷도 쉽게 잘 정리되어 있어서 대만족!",
    createdAt: "2026-07-29T10:12:00",
  },
  {
    id: 2,
    bookId: 2,
    memberId: 88,
    rating: 4,
    content: "개발자 필독서! 명구절들 정리해두기 좋았습니다.",
    createdAt: "2026-07-25T09:40:00",
  },
  {
    id: 3,
    bookId: 3,
    memberId: 45,
    rating: 5,
    content: "저렴하게 잘 구해서 읽었습니다.",
    createdAt: "2026-07-18T20:05:00",
  },
];

export function mockGetReviews(bookId: number | string, page = 0, size = 20): Page<ReviewResponse> {
  const matched = REVIEWS.filter((review) => String(review.bookId) === String(bookId));
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
