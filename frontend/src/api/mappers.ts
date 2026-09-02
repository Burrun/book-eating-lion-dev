// 백엔드 DTO -> UI 도메인 타입 변환.
// 백엔드에 아직 없거나 팀 합의가 안 된 필드는 아래 임시 기본값을 쓴다.
// 상세 내역은 docs/frontend-backend-field-mapping.md 참고.
import type {
  AddressResponse,
  BookDetailResponse,
  BookSummaryResponse,
  BookSynopsisDetailResponse,
  CardResponse,
  MemberResponse,
  Page,
  ReviewResponse,
  SubscriptionResponse,
} from "./types.ts";
import type { Address } from "../types/address.ts";
import type { Book, BookSummary, Review, WebtoonCut } from "../types/book.ts";
import type { Card } from "../types/card.ts";
import type { Member, Subscription } from "../types/member.ts";
import type { Paged } from "../types/common.ts";
import { assertRequiredFields } from "../utils/assertShape.ts";

// --- 임시 기본값 (백엔드 미구현 / 미합의) ---
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
    rating: dto.averageRating,
    category: dto.category,
    coverImageUrl: dto.coverImageUrl ?? null,
    ebookAvailable: dto.ebookAvailable,
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
    rating: dto.averageRating,
    // 상세 페이지에서는 리뷰 목록 API의 totalElements로 다시 덮어써 최신값을 쓴다
    // (ProductDetailPage.tsx 참고) — 여기 값은 그 전까지의 초기 표시용이다.
    reviewCount: dto.reviewCount,
    coverImageUrl: dto.coverImageUrl ?? null,
    shippingNote: DEFAULT_SHIPPING_NOTE, // 백엔드에 배송 정책 없음
    synopsis: dto.description ?? "", // 무료 회원용 줄거리. 백엔드에서 null 가능
    webtoonCuts: [], // 유료 회원용. 별도 API(/synopsis/detail)에서 toWebtoonCuts로 채운다
    reviews: [], // 별도 API(/books/{id}/reviews)에서 toReview로 채운다
    ebookAvailable: dto.ebookAvailable,
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
  // member-v1.yaml Member.required: [id, email, name, role]
  assertRequiredFields("MemberResponse", dto, ["id", "email", "name", "role"]);
  return {
    id: dto.id,
    name: dto.name,
    email: dto.email,
    role: dto.role,
  };
}

// 구독 이력이 없으면 dto가 null(=비구독)이다.
export function toSubscription(dto: SubscriptionResponse | null): Subscription {
  return {
    isActive: dto?.status === "ACTIVE",
    planType: dto?.planType ?? null,
    status: dto?.status ?? null,
    startedAt: dto?.startedAt ?? null,
    expiresAt: dto?.expiresAt ?? null,
  };
}

export function toAddress(dto: AddressResponse): Address {
  // member-v1.yaml Address.required: [id, recipientName, phoneNumber, zipcode, address, isDefault]
  // — Checkout.jsx의 recipient.postalCode/address가 조용히 null로 새던 경로가 바로 여기서
  // 시작될 수 있어(서버가 zipcode/address를 비워 보내면 이후 폼에도 그대로 전파된다) 체크한다.
  assertRequiredFields("AddressResponse", dto, [
    "id",
    "recipientName",
    "phoneNumber",
    "zipcode",
    "address",
    "isDefault",
  ]);
  return {
    id: String(dto.id),
    recipientName: dto.recipientName,
    phoneNumber: dto.phoneNumber,
    zipcode: dto.zipcode,
    address: dto.address,
    detailAddress: dto.detailAddress ?? null,
    isDefault: dto.isDefault,
  };
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
    memberId: dto.memberId,
    // nickname은 작성 당시 스냅샷이라 없을 수도 있다(오래된 데이터 등) — 그때만 대체 표시.
    author: dto.nickname ?? `user_${dto.memberId}`,
    rating: dto.rating,
    date: dto.createdAt.slice(0, 10),
    text: dto.content,
  };
}
