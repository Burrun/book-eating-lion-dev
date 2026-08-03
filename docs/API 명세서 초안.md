💡 공통 규격 (Common Specifications)

인증 방식: JWT (Header Authorization: Bearer {token})

필드 네이밍: camelCase 통일

결제수단(paymentMethod): VIRTUAL_CARD(가상카드) / KAKAO_PAY(카카오페이)

공통 응답 포맷:

JSON

{
"success": true,
"data": { ... },
"error": null
}


0. 장바구니 (Cart)

Method

Endpoint

설명

GET

/api/cart

장바구니 조회

POST

/api/cart

장바구니에 담기

PATCH

/api/cart/{cartItemId}

수량 변경

DELETE

/api/cart/{cartItemId}

장바구니 항목 삭제

POST

/api/cart/checkout

장바구니 → 주문 전환

1. 🔑 인증 / 회원 (Auth & Member)

Method

Endpoint

설명

POST

/api/auth/signup

회원가입

POST

/api/auth/login

로그인 (username 기준)

POST

/api/auth/refresh

토큰 재발급

GET

/api/members/me

내 정보 조회

PATCH

/api/members/me

내 정보 수정

GET

/api/members/me/grade

회원 등급 및 포인트 조회

2. 📚 도서 탐색 (Books & Search)

Method

Endpoint

설명

GET

/api/books

도서 목록 (카테고리/필터/페이징)

GET

/api/books/search?q=

도서 검색 및 자동완성

GET

/api/books/{bookId}

도서 상세 정보

GET

/api/books/bestsellers

베스트셀러 목록

GET

/api/books/new-releases

신간 목록

GET

/api/books/{bookId}/synopsis/detail

구매 인증 전용 상세 줄거리 및 웹툰컷

3. 💬 리뷰 / 찜 / 최근 본 상품 (Review, Wishlist, History)

Method

Endpoint

설명

GET

/api/books/{bookId}/reviews

리뷰 목록 조회

POST

/api/books/{bookId}/reviews

리뷰 작성

DELETE

/api/reviews/{reviewId}

리뷰 삭제

GET

/api/members/me/wishlist

찜 목록 조회

POST

/api/wishlist/{bookId}

찜 추가

DELETE

/api/wishlist/{bookId}

찜 삭제

GET

/api/members/me/recent-books

최근 본 상품 목록

4. 📦 중고 매물 (Used Books & ISBN)

Method

Endpoint

설명

GET

/api/isbn/{isbn}/lookup

ISBN 조회 (카카오/알라딘 API 연동 및 Redis 캐싱)

POST

/api/used-books

중고 매물 등록 (자동완성 정보 + 실물 사진)

POST

/api/used-books/presigned-url

AWS S3 업로드용 Presigned URL 발급

GET

/api/used-books

중고 매물 목록

GET

/api/used-books/{id}

중고 매물 상세

5. 🚚 배송 (Delivery)

Method

Endpoint

설명

GET

/api/members/me/addresses

배송지 목록 조회

POST

/api/members/me/addresses

배송지 등록

GET

/api/orders/{orderId}/delivery

배송 상태 조회

6. 🛡️ 관리자 (Admin)

Method

Endpoint

설명

GET

/api/admin/books

도서 관리 목록

GET

/api/admin/orders

주문 관리 목록

GET

/api/admin/members

회원 관리 목록

GET

/api/admin/dashboard/stats

통계 대시보드 데이터

GET

/api/admin/audit-logs

감사 로그 조회

7. 🎟️ 쿠폰 / 포인트 (Coupon & Point)

Method

Endpoint

설명

GET

/api/members/me/coupons

보유 쿠폰 목록 조회

POST

/api/admin/coupons

쿠폰 발급 (관리자 전용)

GET

/api/members/me/points

포인트 내역 조회

8. 🛒 주문 / 취소 / 환불 (Order & Payment)

Method

Endpoint

설명

POST

/api/orders

주문 생성

GET

/api/orders/{orderId}

주문 상세 조회 (status: PENDING_PAYMENT, PAID 등)

POST

/api/orders/{orderId}/cancel

주문 취소

POST

/api/orders/{orderId}/return

반품 신청

POST

/api/orders/{orderId}/refund

환불 처리

9. 🔔 재입고 / 문의 / FAQ (Alert, Inquiry, FAQ)

Method

Endpoint

설명

POST

/api/books/{bookId}/restock-alert

재입고 알림 신청

GET

/api/books/{bookId}/inquiries

상품 문의 목록

POST

/api/books/{bookId}/inquiries

상품 문의 등록

GET

/api/faq

FAQ 목록

10. 💬 거래 채팅 (WebSocket Chat)

Type

Endpoint / Destination

설명

WS

/ws/chat

STOMP 연결

SUB

/sub/chat/rooms/{roomId}

채팅방 구독

PUB

/pub/chat/rooms/{roomId}

메시지 발행

POST

/api/chat/rooms

채팅방 생성 (매물 기준)

PATCH

/api/chat/rooms/{roomId}/status

거래 상태 변경 (예약중/거래완료) 및 시스템 메시지 발송

11. 🎴 추천 대기열 (Swipe Recommendation)

Method

Endpoint

설명

GET

/api/recommend/queue

추천 카드 목록 조회

POST

/api/recommend/queue/reaction

스와이프 반응 (좋아요/스킵) 기록

12. 📦 구독 서비스 (Subscription - Mock)

Method

Endpoint

설명

POST

/api/subscriptions

구독 신청 (모의 결제)

GET

/api/members/me/subscription

구독 상태 조회

DELETE

/api/subscriptions

구독 해지

13. 🦁 나만의 작은 사자 기록소 (Lion Log)

Method

Endpoint

설명

POST

/api/lion/feed

책 먹이기 (메모/인용 저장 및 성장치 반영)

GET

/api/lion/me

내 사자 상태 조회 (레벨/성장치)

GET

/api/lion/records

저장한 메모/인용 목록 조회

POST

/api/lion/ask

사자에게 물어보기 (RAG 질의, 프리미엄 전용)

14. 💰 마켓플레이스 수수료 / 정산 (Commission & Settlement)

Method

Endpoint

설명

GET

/api/used-books/{id}/commission

거래 성사 시 예상 수수료 계산 조회

GET

/api/admin/settlements

정산 내역 조회 (관리자 전용)

