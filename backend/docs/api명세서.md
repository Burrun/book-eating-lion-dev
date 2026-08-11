
# 📑 백엔드 API 및 시스템 규격 명세서

## 📌 1. 공통 규격 (Common Specifications)

> **인증 방식**: AWS Cognito User Pool 기반 JWT (`Header Authorization: Bearer {accessToken}`)

> **필드 네이밍**: `camelCase` 통일

> **결제 수단 (`paymentMethod`)**: `CARD` (가상카드) / `KAKAOPAY` (카카오페이)

### 🔹 공통 응답 포맷 (Common Response Format)

```json
{
  "success": true,
  "message": null,
  "data": { ... },
  "error": null
}

```

---

## 📋 2. 상세 API 명세

### 🛒 0. 장바구니 (Cart)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/cart` | 장바구니 목록 조회 |
| `POST` | `/api/cart` | 장바구니 담기(이미 담긴 도서면 수량 누적) |
| `PATCH` | `/api/cart/{cartItemId}` | 수량 변경 |
| `DELETE` | `/api/cart/{cartItemId}` | 장바구니 항목 삭제 |

> 💡 **주문 전환 정책**: 별도의 `POST /api/cart/checkout` 엔드포인트는 없다. 장바구니 → 주문 전환은 `POST /api/orders`(주문 생성)가 전담하며, 결제가 완료되면 서버가 해당 도서를 장바구니에서 자동으로 정제한다.

---

### 🔑 1. 인증 / 회원 (Auth & Member)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `POST` | `/api/auth/signup` | 회원가입 (AWS Cognito User Pool 계정 생성) |
| `POST` | `/api/auth/login` | 로그인 (AWS Cognito 인증 및 JWT 토큰 발급) |
| `POST` | `/api/auth/refresh` | 토큰 재발급 (AWS Cognito Refresh Token 기반) |
| `GET` | `/api/members/me` | 내 정보 조회 (Cognito JWT 검증 후 회원 정보 반환) |
| `PATCH` | `/api/members/me` | 내 정보 수정 |

> 💡 **보안 정책**: Spring Security 및 AWS Cognito OAuth2 JWT 검증 필터를 통해 백엔드 API 접근 권한을 제어합니다.

---

### 📚 2. 도서 탐색 (Books & Search)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/books` | 도서 목록 조회 (카테고리/필터/페이징) |
| `GET` | `/api/books/search?q=` | 도서 검색 및 자동완성 |
| `GET` | `/api/books/{bookId}` | 도서 상세 정보 조회 |
| `GET` | `/api/books/bestsellers` | 베스트셀러 목록 조회 |
| `GET` | `/api/books/new-releases` | 신간 목록 조회 |
| `GET` | `/api/books/{bookId}/synopsis/detail` | 상세 줄거리 및 프리미엄 웹툰형 요약 컷 조회 (구매 인증/구독 전용) |

#### 💡 도서 상세 조회 응답 예시 (평점 필드 노출)

> 요청마다 `AVG` 집계를 수행하지 않고, 리뷰 작성/삭제 시 실시간 반정규화 동기화되는 `Book.averageRating`, `Book.reviewCount` 컬럼 값을 direct 반환하여 조회 부하를 절감합니다.

```json
{
  "success": true,
  "data": {
    "bookId": 1,
    "title": "예시 도서",
    "author": "저자명",
    "price": 15000,
    "averageRating": 4.3,
    "reviewCount": 128
  },
  "error": null
}

```

#### 🔒 상세 줄거리(웹툰형 요약) 접근 인증 로직

`GET /api/books/{bookId}/synopsis/detail` 접근 시 JWT의 `sub`(`memberId`) 기준 아래 조건 중 **하나 이상 충족** 시 접근 허용:

1. **구독 활성 상태**: `subscriptions` 테이블에서 해당 `memberId`의 구독 상태가 `ACTIVE`
2. **개별 구매 이력**: `order_items` 테이블에서 해당 `memberId` + `bookId` 조합의 주문이 결제완료(또는 배송완료) 상태

*미충족 시: `403 Forbidden` + `"구매 후 열람 가능합니다"` 응답 반환*

---

### ⭐ 3. 리뷰 / 찜 / 최근 본 상품 (Review, Wishlist, History)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/books/{bookId}/reviews` | 리뷰 목록 조회 |
| `POST` | `/api/books/{bookId}/reviews` | 리뷰 작성 (도서 평점 및 리뷰 수 실시간 반정규화 동기화) |
| `DELETE` | `/api/reviews/{reviewId}` | 리뷰 삭제 (도서 평점 및 리뷰 수 실시간 반정규화 동기화) |
| `GET` | `/api/members/me/wishlist` | 찜 목록 조회 |
| `POST` | `/api/wishlist/{bookId}` | 찜 추가 |
| `DELETE` | `/api/wishlist/{bookId}` | 찜 삭제 |
| `GET` | `/api/members/me/recent-books` | 최근 본 상품 목록 조회 |

> 📌 **최근 본 상품 API 활용 Usecase**
> * **마이페이지**: 도서 상세 조회(`GET /api/books/{bookId}`) 시 `upsert` 훅으로 자동 기록 (회원 전용, 비회원 미기록)
> * **AI 맞춤추천 개인화 시그널**: 섹션 10 추천 대기열(Swipe Recommendation)에서 체류시간·동일도서 조회횟수와 함께 개인화 판단 근거로 재사용
> 
> 

---

### 🚚 4. 배송 (Delivery)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/members/me/addresses` | 배송지 목록 조회 |
| `POST` | `/api/members/me/addresses` | 배송지 등록 |
| `GET` | `/api/orders/{orderId}/delivery` | 배송 상태 조회 |

---

### 🛠️ 5. 관리자 (Admin)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/admin/books` | 도서 관리 목록 조회 |
| `GET` | `/api/admin/orders` | 주문 관리 목록 조회 |
| `GET` | `/api/admin/members` | 회원 관리 목록 조회 |
| `GET` | `/api/admin/dashboard/stats` | 실시간 통계 대시보드 데이터 조회 |
| `GET` | `/api/admin/audit-logs` | 감사 로그 조회 |
| `POST` | `/api/admin/coupons` | 프로모션 쿠폰 발급 (관리자 전용) |

---

### 🎟️ 6. 쿠폰 (Coupon)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/coupons/me` | 보유 쿠폰 목록 조회 (미사용·미만료 쿠폰만) |
| `POST` | `/api/coupons/register` | 쿠폰 코드로 등록/발급 |

> 💡 **운영 정책**: 포인트 및 등급제 제거 정책에 따라 등급별 자동 발급 규정을 폐지하고 프로모션용 쿠폰 발급 체계로 전환하여 운용합니다.

---

### 🪪 6-1. 가상카드 (Virtual Card)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `POST` | `/api/cards` | 가상카드 신규 발급 (monthlyLimit 생략 시 기본 1,000,000원) |
| `GET` | `/api/cards/me` | 내 보유 가상카드 목록 조회 |
| `PATCH` | `/api/cards/{cardId}/status` | 카드 상태 변경(ACTIVE/SUSPENDED/CLOSED, CLOSED 는 종단 상태) |

> 💡 **결제 연동**: 한도 차감/복구는 order-service 가 `/internal/cards/{cardId}/deduct`, `/internal/cards/{cardId}/restore` 를 통해 서비스 간 호출로 처리하며, 이 두 API 는 클러스터 내부 전용(Ingress 미노출)이라 외부 명세에는 포함하지 않는다.

---

### 💳 7. 주문 / 취소 / 반품 / 환불 (Order & Payment)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `POST` | `/api/orders` | 주문 생성. `paymentMethod=CARD` 는 결제 승인까지 1단계로 끝나고, `paymentMethod=KAKAOPAY` 는 카카오페이 ready 만 수행하고 `nextRedirectUrl` 을 반환한다(2단계 중 1단계) |
| `GET` | `/api/orders/{orderId}` | 주문 상세 조회 |
| `POST` | `/api/orders/{orderId}/cancel` | 주문 취소 (PAID 상태에서만 가능. 재고/쿠폰/카드한도 복구 또는 카카오페이 취소를 단일 트랜잭션으로 처리) |
| `POST` | `/api/orders/{orderId}/return` | 반품/교환 신청 (PAID 상태에서만 가능. 재고·쿠폰·결제는 아직 건드리지 않고 orderStatus 를 RETURN_REQUESTED 로 바꾸고 사유만 저장) |
| `POST` | `/api/orders/{orderId}/refund` | 환불 처리 (RETURN_REQUESTED 상태에서만 가능. 카드 한도 복구 또는 카카오페이 취소 API 호출, 재고 복구, 쿠폰 원복까지 완료하고 orderStatus 를 REFUNDED 로 전환) |
| `POST` | `/api/payments/kakao/approve` | 카카오페이 결제 승인(2단계 중 2단계). 카카오 리다이렉트 콜백에서 받은 `orderId`, `pgToken` 을 전달하면 결제를 최종 승인하고 orderStatus 를 PAID 로 전환한다 |

> 💡 **주문 상태 머신**: `PENDING_PAYMENT`(KAKAOPAY ready 직후) → `PAID` → `CANCELLED`(사전 취소) 또는 `RETURN_REQUESTED` → `REFUNDED`. 취소(cancel)와 반품(return→refund)은 별개 흐름이다 — 취소는 PAID 에서 즉시 종결되고, 반품은 신청과 환불이 분리되어 있다.

---

### 🔔 8. 재입고 / 문의 / FAQ (Alert, Inquiry, FAQ)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `POST` | `/api/books/{bookId}/restock-alert` | 재입고 알림 신청 |
| `GET` | `/api/books/{bookId}/inquiries` | 상품 문의 목록 조회 |
| `POST` | `/api/books/{bookId}/inquiries` | 상품 문의 등록 |
| `GET` | `/api/faq` | FAQ 목록 조회 |

---

### 💬 9. 1:1 문의 채팅 (WebSocket Chat)

| Type | Endpoint / Destination | 설명 |
| --- | --- | --- |
| `WS` | `/ws/chat` | STOMP 웹소켓 연결 (Cognito JWT 인증 처리) |
| `SUB` | `/sub/chat/rooms/{roomId}` | 채팅방 수신 구독 |
| `PUB` | `/pub/chat/rooms/{roomId}` | 메시지 발행 |
| `POST` | `/api/chat/rooms` | 1:1 문의 채팅방 생성 (구매자-운영자 간) |
| `GET` | `/api/chat/rooms` | 문의 채팅방 목록 조회 (관리자/사용자 권한별) |
| `GET` | `/api/chat/rooms/{roomId}/messages` | 이전 채팅 메시지 내역 조회 |

> 📌 **시스템 구조**:
> * 기존 중고 거래 채팅을 대체하여 구매자 및 운영자(`ADMIN` 권한 계정) 간 1:1 실시간 문의 시스템으로 전환
> * 다중 Pod 환경 내 메시지 유실 방지를 위해 **Redis Pub/Sub 기반 브로드캐스팅 구조** 적용
> 
> 

---

### 🎴 10. 추천 대기열 (Swipe Recommendation)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/recommend/queue` | 추천 카드 목록 조회 |
| `POST` | `/api/recommend/queue/reaction` | 카드 스와이프 반응 (좋아요/스킵) 기록 |

> 💡 **추천 범위**: 비회원 개인화 추천은 MVP 범위에서 제외하고 로그인 사용자에게만 적용합니다. (비회원에게는 베스트셀러/신간 노출로 대체)

---

### 👑 11. 정기 구독 서비스 (Subscription - 단일 핵심 BM)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `POST` | `/api/subscriptions` | 정기구독 신청 (모의 결제) |
| `GET` | `/api/members/me/subscription` | 구독 상태 조회 |
| `DELETE` | `/api/subscriptions` | 정기구독 해지 |

🎁 **정기구독 단일 혜택 패키지** (결제 시 일괄 제공):

* 📖 전체 eBook 무제한 열람 권한 부여
* 🦁 **사자 프리미엄 기능**: 개인 독서 메모 기반 RAG 자연어 질의응답 이용 권한 부여
* 🖼️ 4컷 웹툰 요약 컷 접근 권한 부여
*(※ 실물 도서 구매는 구독과 별개로 개별 진행)*

---

### 🦁 12. 나만의 작은 사자 기록소 (Lion Log)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `POST` | `/api/lion/feed` | 책 먹이기 (메모/인용 저장 및 캐릭터 성장치 반영) |
| `GET` | `/api/lion/me` | 내 사자 상태 조회 (레벨/성장치) |
| `GET` | `/api/lion/records` | 저장한 메모 및 인용 목록 조회 |
| `POST` | `/api/lion/ask` | 사자에게 물어보기 (RAG 기반 메모/인용 유사도 자연어 검색, 구독 회원 전용) |

---

### 📖 13. Ebook 뷰어 (Ebook Viewer)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/books/{bookId}/ebook` | eBook 열람 가능 여부 및 Presigned URL 조회 (구독자 전용) |

> 📌 **기술 사양 및 운영 방침**:
> * **뷰어 구현**: `react-reader` (오픈소스, `epub.js` 기반) 사용
> * **연동 대상**: 저작권이 만료된 공개 EPUB 4권 연동
> 1. 이상한 나라의 앨리스
> 2. 셜록 홈즈 시리즈
> 3. 프랑켄슈타인
> 4. 오만과 편견
> 
> 
> * **기타 도서**: `ebookAvailable: false` 반환 처리
> * **보안 및 스트리밍**: EPUB 데이터는 **AWS S3**에 저장되며, 서버 부하 방지 및 저작권 보호를 위해 **Presigned URL** 방식으로 클라이언트에 안전하게 스트리밍 전달
> 
> 
