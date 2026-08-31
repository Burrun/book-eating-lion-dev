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

export type BookDetailResponse = Catalog["BookDetail"];
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

// GET /api/catalog/books/{bookId}/ebook
export interface EbookAccessResponse {
  bookId: number;
  ebookAvailable: boolean;
  presignedUrl: string | null;
  expiresAt: string | null;
}

export interface ReadingProgressRequest {
  cfi: string;
  percentage?: number;
}

// --- 신간/EPUB 업로드 (관리자) ---
export type AdminBookResponse = Catalog["AdminBookResponse"];
export type AdminBookCreateRequest = Catalog["AdminBookCreateRequest"];
export type AdminBookUpdateRequest = Catalog["AdminBookUpdateRequest"];
export type EpubUploadUrlRequest = Catalog["EpubUploadUrlRequest"];
export type EpubUploadUrlResponse = Catalog["EpubUploadUrlResponse"];

// --- 카테고리 (Category, 관리자) ---
export type CategoryResponse = Catalog["Category"];
export type CategoryCreateRequest = Catalog["CategoryCreateRequest"];
export type CategoryUpdateRequest = Catalog["CategoryUpdateRequest"];
// CategoryCreateRequest/UpdateRequest는 계약상 같은 모양이라(allOf) 폼 하나로 같이 쓴다.
export type CategoryRequest = CategoryCreateRequest;

// --- FAQ (관리자) ---
export type FaqResponse = Catalog["FaqResponse"];
export type FaqWriteRequest = Catalog["FaqWriteRequest"];
export type FaqRequest = FaqWriteRequest;

// --- 정기구독 배너 (관리자) ---
export type SubscriptionBannerResponse = Catalog["SubscriptionBannerResponse"];
export type SubscriptionBannerWriteRequest = Catalog["SubscriptionBannerWriteRequest"];

// --- 상품문의 (Inquiry, 관리자) ---
export type InquiryResponse = Catalog["InquiryResponse"];
export type InquiryAnswerRequest = Catalog["InquiryAnswerRequest"];
export type InquiryStatus = NonNullable<Catalog["InquiryResponse"]["status"]>;
export type InquiryRequest = Catalog["InquiryWriteRequest"];

// --- 재입고 알림 ---
// 계약(catalog-v1.yaml)에 아직 응답 스키마가 없어 수기로 둔다. 계약이 추가되면 위의
// Catalog[...] 파생 패턴으로 옮긴다.
export type RestockAlertStatus = "WAITING" | "SENT" | "FAILED" | "CANCELLED";

export interface RestockAlertResponse {
  restockAlertId: number;
  bookId: number;
  title: string;
  status: RestockAlertStatus;
  retryCount: number;
  requestedAt: string;
  notifiedAt: string | null;
  cancelledAt: string | null;
}

// GET /api/catalog/recommend/queue
export interface RecommendationCardResponse {
  bookId: number;
  title: string;
  author: string;
  category: string;
  price: number;
  coverImageUrl: string | null;
  score: number;
  recommendationReason: string;
}

export interface RecommendationQueueResponse {
  queueId: string;
  cards: RecommendationCardResponse[];
}

export type RecommendationAction = "LIKE" | "SKIP";

export interface RecommendationReactionRequest {
  queueId: string;
  bookId: number;
  action: RecommendationAction;
}

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

// --- 쿠폰 정책 (Coupon, 관리자) ---
export type CouponResponse = Order["CouponResponse"];
export type CouponCreateRequest = Order["CouponCreateRequest"];
export type CouponUpdateRequest = Order["CouponUpdateRequest"];

// --- 보유 쿠폰 (MemberCoupon, 일반 사용자) ---
// GET /api/coupons/me 로 목록, POST /api/coupons/register 로 코드 입력 발급.
export type MemberCouponView = Order["MemberCouponView"];
export type RegisterCouponRequest = Order["RegisterCouponRequest"];

// --- 장바구니 (Cart) ---
export type CartItemView = Order["CartItemView"];
export type CartResponse = Order["CartResponse"];
export type AddCartItemRequest = Order["AddCartItemRequest"];
export type ChangeCartItemQuantityRequest = Order["ChangeCartItemQuantityRequest"];

// --- AI 상담 (Chat) ---
// POST /api/ai/bot/chat/ticket — /ws/ai/chat 접속용 1회성 교환권
export interface ChatTicketResponse {
  ticket: string;
  expiresInSeconds: number;
}

// --- 주문 (Order) ---
export type OrderItemRequest = Order["OrderItemRequest"];
export type OrderRecipient = Order["Recipient"];
export type CreateOrderRequest = Order["CreateOrderRequest"];
export type OrderResponse = Order["OrderResponse"];
export type OrderSummaryResponse = Order["OrderSummaryResponse"];
export type RequestReturnRequest = Order["RequestReturnRequest"];
export type PaymentMethod = NonNullable<Order["CreateOrderRequest"]["paymentMethod"]>;
export type OrderStatus = NonNullable<Order["OrderResponse"]["orderStatus"]>;

// --- 배송 (Delivery) ---
export type DeliveryResponse = Order["DeliveryResponse"];
export type UpdateDeliveryStatusRequest = Order["UpdateDeliveryStatusRequest"];
export type DeliveryStatus = NonNullable<Order["DeliveryResponse"]["deliveryStatus"]>;

// --- 주문/배송 (관리자) ---
export type AdminOrderSummaryResponse = Order["AdminOrderSummaryResponse"];
