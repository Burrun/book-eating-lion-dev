// 찜목록 목업. 백엔드 DTO(BookSummaryResponse) 형태로 두어 실 API 전환 시
// 매퍼/컴포넌트를 그대로 재사용할 수 있게 한다.
import type { BookSummaryResponse } from "../api/types.ts";

const INITIAL_WISHLIST: BookSummaryResponse[] = [
  {
    id: 1,
    title: "자바 ORM 표준 JPA 프로그래밍",
    author: "김영한",
    price: 38700,
    coverImageUrl: null,
    category: "IT/개발",
    saleStatus: "ON_SALE",
  },
  {
    id: 3,
    title: "해리 포터와 마법사의 돌",
    author: "J.K. 롤링",
    price: 15400,
    coverImageUrl: null,
    category: "소설",
    saleStatus: "ON_SALE",
  },
  {
    id: 11,
    title: "사피엔스",
    author: "유발 하라리",
    price: 22000,
    coverImageUrl: null,
    category: "인문",
    saleStatus: "OUT_OF_STOCK",
  },
  {
    id: 17,
    title: "아주 작은 습관의 힘",
    author: "제임스 클리어",
    price: 16500,
    coverImageUrl: null,
    category: "자기계발",
    saleStatus: "ON_SALE",
  },
];

// 목업 모드에서 추가/삭제가 화면에 반영되도록 모듈 수준에서 상태를 들고 있는다.
// (새로고침하면 초기값으로 돌아간다 — 목업 한정 동작)
let wishlist: BookSummaryResponse[] = [...INITIAL_WISHLIST];

export function mockGetWishlist(): BookSummaryResponse[] {
  return wishlist;
}

export function mockRemoveFromWishlist(bookId: number | string): void {
  wishlist = wishlist.filter((book) => String(book.id) !== String(bookId));
}

export function mockAddToWishlist(bookId: number | string): void {
  if (wishlist.some((book) => String(book.id) === String(bookId))) return;
  const source = INITIAL_WISHLIST.find((book) => String(book.id) === String(bookId));
  if (source) wishlist = [...wishlist, source];
}
