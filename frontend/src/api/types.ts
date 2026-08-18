// 계약(backend/contracts/*.yaml)에서 생성한 타입에 프론트가 쓰던 이름을 붙여 다시 내보내는
// 파사드다. 손으로 적던 형태를 여기서 없앴다 — 계약이 바뀌면 추측이 아니라 타입 에러로
// 드러나야 한다.
//
// 🔴 여기에 계약에 없는 필드를 덧붙이지 않는다. 부득이하면 optional 로 두고, 왜 없는지와
//    언제 지울지를 같이 적는다. 규칙과 그 근거는 docs/frontend/type-generation.md §4.
//
// 생성 방법은 README, 상세는 docs/frontend/type-generation.md.
// 이 파일을 openapi-typescript 의 -o 대상으로 지정하지 말 것(아래 래퍼가 사라진다).
import type { components as CatalogComponents } from "./generated/catalog.ts";
import type { components as MemberComponents } from "./generated/member.ts";
import type { components as OrderComponents } from "./generated/order.ts";

type Catalog = CatalogComponents["schemas"];
type MemberSchemas = MemberComponents["schemas"];
type Order = OrderComponents["schemas"];

// --- 계약에 없는 것 ---
// ApiResponse/Page 는 제네릭 래퍼라 OpenAPI 스키마로 표현되지 않는다.
// 계약에는 도메인마다 *Envelope 로 펼쳐져 있고, 프론트는 unwrap() 으로 벗겨 쓴다.

// 백엔드 공통 응답 래퍼 (common/dto/ApiResponse.java)
export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T;
  error: { code: string; message: string } | null;
}

// Spring Data Page 직렬화 형태 (필요한 필드만)
export interface Page<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// --- 도서 (Catalog) ---
export type BookSummaryResponse = Catalog["BookSummary"];

// 🔴 ebookAvailable / ebookUrl 은 백엔드에 없다. Book 엔티티에 해당 컬럼이 없고
// 전자책 조회 API 도 아직 없다 — 지금은 mock 만 채워 준다.
// 실 API 모드에서는 항상 undefined 이므로 매퍼가 false/null 로 떨어뜨린다.
// 전자책 계약이 생기면 이 교차 타입을 지우고 생성 타입만 쓰면 된다.
export type BookDetailResponse = Catalog["BookDetail"] & {
  ebookAvailable?: boolean;
  ebookUrl?: string | null;
};
export type BookSynopsisDetailResponse = Catalog["BookSynopsisDetail"];
export type ReviewResponse = Catalog["Review"];
export type ReviewRequest = Catalog["ReviewRequest"];
export type ReviewUpdateRequest = Catalog["ReviewUpdateRequest"];
export type SaleStatus = NonNullable<Catalog["BookSummary"]["saleStatus"]>;

// PUT/GET /api/catalog/books/{bookId}/reading-progress — 기록 없으면 data: null
export interface ReadingProgressResponse {
  bookId: number;
  cfi: string;
  percentage: number | null;
  updatedAt: string;
}

export interface ReadingProgressRequest {
  cfi: string;
  percentage?: number;
}

// --- 카테고리 (Category, 관리자) ---
export type CategoryResponse = Catalog["Category"];
export type CategoryCreateRequest = Catalog["CategoryCreateRequest"];
export type CategoryUpdateRequest = Catalog["CategoryUpdateRequest"];

// --- FAQ (관리자) ---
export type FaqResponse = Catalog["FaqResponse"];
export type FaqWriteRequest = Catalog["FaqWriteRequest"];

// --- 상품문의 (Inquiry, 관리자) ---
export type InquiryResponse = Catalog["InquiryResponse"];
export type InquiryAnswerRequest = Catalog["InquiryAnswerRequest"];
export type InquiryStatus = NonNullable<Catalog["InquiryResponse"]["status"]>;

// --- 회원 (Member) ---
export type MemberResponse = MemberSchemas["Member"];
export type Role = NonNullable<MemberSchemas["Member"]["role"]>;
export type Gender = NonNullable<MemberSchemas["Member"]["gender"]>;

// --- 배송지 (Address) ---
export type AddressResponse = MemberSchemas["Address"];
export type AddressCreateRequest = MemberSchemas["AddressCreateRequest"];
export type AddressUpdateRequest = MemberSchemas["AddressUpdateRequest"];

// --- 가상 카드 (Card) ---
export type CardResponse = MemberSchemas["Card"];
export type CardIssueRequest = MemberSchemas["IssueCardRequest"];
export type CardUpdateRequest = MemberSchemas["ChangeCardStatusRequest"];
export type CardStatus = NonNullable<MemberSchemas["Card"]["cardStatus"]>;

// --- 정기구독 (Subscription) ---
// GET/POST/DELETE /api/members/me/subscription. 구독 이력이 없으면 data: null.
export type SubscriptionResponse = MemberSchemas["Subscription"];
export type SubscribeRequest = MemberSchemas["SubscribeRequest"];
export type SubscriptionStatus = NonNullable<MemberSchemas["Subscription"]["status"]>;
export type PlanType = NonNullable<MemberSchemas["Subscription"]["planType"]>;

// --- 장바구니 (Cart) ---
export type CartItemView = Order["CartItemView"];
export type CartResponse = Order["CartResponse"];
export type AddCartItemRequest = Order["AddCartItemRequest"];
export type ChangeCartItemQuantityRequest = Order["ChangeCartItemQuantityRequest"];

// --- 주문 (Order) ---
export type OrderItemRequest = Order["OrderItemRequest"];
export type OrderRecipient = Order["Recipient"];
export type CreateOrderRequest = Order["CreateOrderRequest"];
export type OrderResponse = Order["OrderResponse"];
export type PaymentMethod = NonNullable<Order["CreateOrderRequest"]["paymentMethod"]>;
export type OrderStatus = NonNullable<Order["OrderResponse"]["orderStatus"]>;
