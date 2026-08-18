// 백엔드 DTO -> UI 도메인 타입 변환.
// 백엔드에 아직 없거나 팀 합의가 안 된 필드는 아래 임시 기본값을 쓴다.
// 상세 내역은 docs/frontend-backend-field-mapping.md 참고.
import type {
  BookDetailResponse,
  BookSummaryResponse,
  BookSynopsisDetailResponse,
  CardResponse,
  MemberResponse,
  Page,
  ReviewResponse,
  SubscriptionResponse,
} from "./types.ts";
import type { Book, BookSummary, Review, WebtoonCut } from "../types/book.ts";
import type { Card } from "../types/card.ts";
import type { Member, Subscription } from "../types/member.ts";
import type { Paged } from "../types/common.ts";

// --- 임시 기본값 (백엔드 미구현 / 미합의) ---
const DEFAULT_RATING = 0;
const DEFAULT_REVIEW_COUNT = 0;
const DEFAULT_SHIPPING_NOTE = "배송비 3,000원 (3만원 이상 구매시 무료배송)";

/** Spring Page<T> -> UI Paged<U> */
export function toPaged<T, U>(page: Page<T>, mapItem: (item: T) => U): Paged<U> {
  return {
    items: page.content.map(mapItem),
    page: page.number,
    totalPages: page.totalPages,
    totalElements: page.totalElements,
  };
}

export function toBookSummary(dto: BookSummaryResponse): BookSummary {
  return {
    id: String(dto.id),
    title: dto.title,
    price: dto.price,
    rating: DEFAULT_RATING, // 백엔드에 평점 없음
    category: dto.category,
  };
}

export function toBook(dto: BookDetailResponse): Book {
  return {
    id: String(dto.id),
    title: dto.title,
    author: dto.author,
    publisher: dto.publisher,
    isbn: dto.isbn,
    price: dto.price,
    rating: DEFAULT_RATING, // 백엔드에 평점 없음
    reviewCount: DEFAULT_REVIEW_COUNT, // 리뷰 목록 API의 totalElements로 별도 주입
    shippingNote: DEFAULT_SHIPPING_NOTE, // 백엔드에 배송 정책 없음
    synopsis: dto.description ?? "", // 무료 회원용 줄거리. 백엔드에서 null 가능
    webtoonCuts: [], // 유료 회원용. 별도 API(/synopsis/detail)에서 toWebtoonCuts로 채운다
    reviews: [], // 별도 API(/books/{id}/reviews)에서 toReview로 채운다
    // 백엔드에 전자책 필드가 없다 — 실 API 모드에서는 항상 이 기본값이다(types.ts 참고).
    ebookAvailable: dto.ebookAvailable ?? false,
    ebookUrl: dto.ebookUrl ?? null,
  };
}

// 백엔드는 웹툰 컷 배열이 아니라 줄거리 텍스트 하나만 준다.
// 컷 분할 규칙이 정해지기 전까지 문단 단위로 나눠 임시 매핑한다.
export function toWebtoonCuts(dto: BookSynopsisDetailResponse): WebtoonCut[] {
  // detailedSynopsis 는 nullable 이다. 아직 안 채운 책이면 컷이 없다.
  return (dto.detailedSynopsis ?? "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((caption, i) => ({ id: `cut-${i + 1}`, caption }));
}

export function toMember(dto: MemberResponse): Member {
  return {
    id: dto.id,
    name: dto.name,
    email: dto.email,
  };
}

// 구독 이력이 없으면 dto가 null(=비구독)이다.
export function toSubscription(dto: SubscriptionResponse | null): Subscription {
  return { isActive: dto?.status === "ACTIVE" };
}

export function toCard(dto: CardResponse): Card {
  return {
    id: String(dto.cardId),
    maskedNumber: dto.maskedCardNumber,
    status: dto.cardStatus,
    monthlyLimit: dto.monthlyLimit,
    currentUsage: dto.currentUsage,
    availableLimit: Math.max(dto.monthlyLimit - dto.currentUsage, 0),
    // 한도가 0이면 나눗셈이 Infinity/NaN 이 되므로 방어한다.
    usageRatio: dto.monthlyLimit > 0 ? Math.min(dto.currentUsage / dto.monthlyLimit, 1) : 0,
    issuedDate: dto.issuedDate,
    expiryDate: dto.expiryDate,
  };
}

export function toReview(dto: ReviewResponse): Review {
  return {
    id: String(dto.id),
    author: `user_${dto.memberId}`, // 백엔드가 작성자 표시명을 주지 않음
    rating: dto.rating,
    date: dto.createdAt.slice(0, 10),
    text: dto.content,
  };
}
