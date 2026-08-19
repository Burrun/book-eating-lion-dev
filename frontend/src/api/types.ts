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

// GET /api/catalog/books, /bestsellers, /new-releases, /wishlist/me, /recent-books/me
export interface BookSummaryResponse {
  id: number;
  title: string;
  author: string;
  price: number;
  coverImageUrl: string | null;
  category: string;
  saleStatus: SaleStatus;
}

// GET /api/catalog/books/{bookId}
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
  ebookAvailable: boolean;
}

// GET /api/catalog/books/{bookId}/ebook
export interface EbookAccessResponse {
  bookId: number;
  ebookAvailable: boolean;
  presignedUrl: string | null;
  expiresAt: string | null;
}

export interface ReadingProgressResponse {
  bookId: number;
  cfi: string;
  percentage: number | null;
  updatedAt: string;
}

// GET /api/catalog/books/{bookId}/synopsis/detail
export interface BookSynopsisDetailResponse {
  bookId: number;
  title: string;
  detailedSynopsis: string;
}

// GET/POST /api/catalog/books/{bookId}/reviews
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

// --- 장바구니 (Cart) ---
// GET /api/cart, POST /api/cart, PATCH /api/cart/{cartItemId} 의 items 항목
export interface CartItemView {
  cartItemId: number;
  bookId: number;
  title: string;
  price: number;
  coverImageUrl: string | null;
  quantity: number;
  subtotal: number;
}

// GET /api/cart
export interface CartResponse {
  items: CartItemView[];
  totalQuantity: number;
  totalPrice: number;
}

// POST /api/cart (quantity 생략 시 서버 기본값 1)
export interface AddCartItemRequest {
  bookId: number;
  quantity?: number;
}

// PATCH /api/cart/{cartItemId}
export interface ChangeCartItemQuantityRequest {
  quantity: number;
}
// DELETE /api/cart/{cartItemId} — 204 No Content (응답 바디 없음, 별도 타입 없음)

// --- AI 상담 (Chat) ---
// POST /api/ai/bot/chat/ticket — /ws/ai/chat 접속용 1회성 교환권
export interface ChatTicketResponse {
  ticket: string;
  expiresInSeconds: number;
}

// --- 주문 (Order) ---
// 이번 스코프는 주문 생성 + 카드/무통장 결제만. KAKAOPAY도 같은 enum 값을 쓰지만
// 결제 승인(POST /api/payments/kakao/approve)은 별도 작업으로 미룬다.
export type PaymentMethod = "KAKAOPAY" | "VIRTUAL_CARD" | "BANK_TRANSFER";
// 실제 enum 미확인 — 카드/무통장 결제 응답에서 관찰되는 값 기준으로 추정.
export type OrderStatus = "PENDING" | "PAID" | "CANCELLED";

export interface OrderItemRequest {
  bookId: number;
  quantity: number;
}

// 필드명 미확정 — member-service AddressCreateRequest 컨벤션(recipientName/phoneNumber/...)을 따라 추정.
export interface OrderRecipient {
  recipientName: string;
  phoneNumber: string;
  zipcode: string;
  address: string;
  detailAddress: string;
  deliveryRequest?: string;
}

// POST /api/orders
export interface CreateOrderRequest {
  items: OrderItemRequest[];
  memberCouponId: number | null;
  recipient: OrderRecipient;
  paymentMethod: PaymentMethod;
  cardId: number | null;
}

// POST /api/orders 응답 — 상세 스펙 미확인, orderId/status만 실제로 쓰고 있음.
export interface OrderResponse {
  orderId: number;
  status: OrderStatus;
}
