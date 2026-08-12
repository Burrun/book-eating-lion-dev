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

export type SaleStatus = "ON_SALE" | "STOPPED" | "OUT_OF_STOCK";

// GET /api/books, /bestsellers, /new-releases, /members/me/wishlist, /members/me/recent-books
export interface BookSummaryResponse {
  id: number;
  title: string;
  author: string;
  price: number;
  coverImageUrl: string | null;
  category: string;
  saleStatus: SaleStatus;
}

// GET /api/books/{bookId}
export interface BookDetailResponse {
  id: number;
  title: string;
  author: string;
  publisher: string;
  isbn: string;
  category: string;
  price: number;
  stockQuantity: number;
  coverImageUrl: string | null;
  description: string;
  saleStatus: SaleStatus;
  publishedDate: string;
  createdAt: string;
  updatedAt: string;
}

// GET /api/books/{bookId}/synopsis/detail
export interface BookSynopsisDetailResponse {
  bookId: number;
  title: string;
  detailedSynopsis: string;
}

// GET/POST /api/books/{bookId}/reviews
export interface ReviewResponse {
  id: number;
  bookId: number;
  memberId: number;
  rating: number;
  content: string;
  createdAt: string;
}

export interface ReviewRequest {
  rating: number;
  content: string;
}

// --- 회원 (Member) ---
export type Role = "USER" | "ADMIN";
export type Gender = "MALE" | "FEMALE";

// GET /api/members/me
export interface MemberResponse {
  id: number;
  email: string;
  name: string;
  phoneNumber: string;
  gender: Gender;
  birthDate: string;
  role: Role;
}

// --- 구독 (Subscription) ---
export type SubscriptionStatus = "ACTIVE" | "CANCELLED";

// GET /api/members/me/subscription (구독 이력 없으면 data: null)
export interface SubscriptionResponse {
  status: SubscriptionStatus;
  planName: string;
  monthlyPrice: number;
  nextDeliveryDate: string | null;
  cancelledAt: string | null;
}

// --- 가상 카드 (Card) ---
// member-service 실제 명세 기준 (backend/docs/member-service-spec.md 섹션 2.4).
// card_token 은 결제 토큰이라 프론트로 내려오면 안 되므로 응답에 포함되지 않는다.
export type CardStatus = "ACTIVE" | "SUSPENDED" | "CLOSED";

// GET /api/cards/me, POST /api/cards, PATCH /api/cards/{cardId}/status
export interface CardResponse {
  cardId: number;
  maskedCardNumber: string;
  cardStatus: CardStatus;
  monthlyLimit: number;
  currentUsage: number;
  issuedDate: string;
  expiryDate: string;
}

// POST /api/cards (body 자체도 생략 가능)
export interface CardIssueRequest {
  monthlyLimit?: number;
}

// PATCH /api/cards/{cardId}/status — 카드 상태 변경만 지원한다(월 한도 변경 API는 없음).
export interface CardUpdateRequest {
  cardStatus: CardStatus;
}
