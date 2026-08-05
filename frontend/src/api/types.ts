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
export type MemberGrade = "BASIC" | "PREMIUM";
export type Role = "USER" | "ADMIN";
export type Gender = "MALE" | "FEMALE";

// GET /api/members/me/grade
export interface MemberGradeResponse {
  grade: MemberGrade;
  point: number;
}

// GET /api/members/me
export interface MemberResponse {
  id: number;
  email: string;
  name: string;
  phoneNumber: string;
  gender: Gender;
  birthDate: string;
  role: Role;
  grade: MemberGrade;
  point: number;
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
// 주의: API 명세서 초안에 카드 엔드포인트가 아직 없다.
// 아래 필드는 db/schema.sql 의 cards 테이블을 기준으로 정의했고,
// 경로는 기존 명세 컨벤션(/api/members/me/*)을 따랐다. 백엔드 확정 시 조정 필요.
// card_token 은 결제 토큰이라 프론트로 내려오면 안 되므로 의도적으로 제외했다.
export type CardStatus = "ACTIVE" | "SUSPENDED" | "TERMINATED";

// GET /api/members/me/cards
export interface CardResponse {
  id: number;
  cardCompany: string | null;
  maskedCardNumber: string;
  cardStatus: CardStatus;
  monthlyLimit: number;
  currentUsage: number;
  virtualBalance: number;
  isDefault: boolean;
  issuedDate: string;
  expiryDate: string;
}

// POST /api/members/me/cards
export interface CardIssueRequest {
  monthlyLimit: number;
  cardCompany?: string;
}

// PATCH /api/cards/{cardId}
export interface CardUpdateRequest {
  monthlyLimit?: number;
  cardStatus?: CardStatus;
}
