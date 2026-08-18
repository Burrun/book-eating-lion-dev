// GET /api/members/me 응답 형태 — MemberResponse
//
// level·exp 는 여기 없다. 사자는 ai 서비스 소유라 GET /api/ai/lion/me 가 준다
// (MOCK_LION_STATUS). 예전에는 두 API 의 필드가 이 상수 하나에 섞여 있었다.
//
// badges·streakCount 는 어느 API 에도 없다 — 백엔드 미구현이라 mock 전용이며,
// 화면도 USE_MOCK 일 때만 배지를 그린다.
export const MOCK_PROFILE = {
  id: "9f8c1a2b-3d4e-5f60-7a8b-9c0d1e2f3a4b",
  email: "hong@example.com",
  name: "홍길동",
  phoneNumber: "010-1234-5678",
  gender: "MALE",
  birthDate: "1995-03-21",
  role: "USER",
};

// mock 전용. 대응 API 가 없다.
export const MOCK_BADGES = [
  { type: "achievement", label: "전체 10건 달성" },
  { type: "reading", label: "독서량 41권" },
  { type: "streak", label: "7일 연속 출석" },
];

// "streak" 배지의 불꽃 연출 임계값(3일 이상/7일 이상) 판단에만 쓰인다. 배지 라벨 텍스트와 맞춰둘 것.
export const MOCK_STREAK_COUNT = 7;

// GET /api/ai/lion/me 응답 형태 — LionStatus
//
// 🔴 exp 는 누적값이다. 백엔드가 level = 1 + exp/100 으로 계산하므로 이 둘은 항상 맞아야 한다.
// 여기서는 120 → 1 + 120/100 = 2.
export const MOCK_LION_STATUS = {
  exp: 120,
  level: 2,
  growthStage: "CUB",
  fedBookCount: 3,
};

// GET /api/ai/lion/feedable-books 응답 형태 — { bookId, title, pages }
//
// 책마다 다른 exp 를 주던 예전 형태를 버렸다. 백엔드는 책 종류·페이지 수와 무관하게
// Lion.EXP_PER_FEED(40)를 고정으로 주며(FeedService), 그래서 이 응답에 exp 가 없다.
// 8권을 다 먹이면 누적 120 + 320 = 440 → level = 1 + 440/100 = 5.
export const MOCK_FED_BOOKS = [
  { bookId: 1, title: "클린 코드", pages: 42 },
  { bookId: 2, title: "해리 포터와 마법사의 돌", pages: 61 },
  { bookId: 3, title: "역행자", pages: 38 },
  { bookId: 4, title: "언어의 온도", pages: 29 },
  { bookId: 5, title: "부의 추월차선", pages: 47 },
  { bookId: 6, title: "사피엔스", pages: 88 },
  { bookId: 7, title: "미드나잇 라이브러리", pages: 35 },
  { bookId: 8, title: "이펙티브 자바", pages: 54 },
];

export const MOCK_READING_NOTES = [
  { id: 1, book: "클린 코드", quote: "“단순함은 모든 것의 시작이다.”" },
  {
    id: 2,
    book: "자바 ORM 표준 JPA 프로그래밍",
    quote: "“영속성 컨텍스트는 1차 캐시를 제공한다.”",
  },
];

// POST /api/ai/lion/ask 응답 형태 — AskResult
// citations[].score 는 0~1 이고 클수록 유사하다(S3 Vectors 의 거리를 1-distance 로 뒤집은 값).
export const MOCK_RAG_ANSWER = {
  mode: "answer",
  grounded: true,
  answer:
    "[자바 ORM 표준 JPA 프로그래밍] 에서 '캡슐화와 접근 제어자' 구절을 찾았습니다.[1] 필드는 private 으로 감추고 접근자를 통해서만 노출하라는 내용입니다.",
  citations: [
    {
      ref: 1,
      bookId: 1,
      bookTitle: "자바 ORM 표준 JPA 프로그래밍",
      page: 87,
      snippet: "필드는 private 으로 선언하고 필요한 경우에만 접근자를 열어 둔다…",
      score: 0.92,
    },
  ],
};

export const MOCK_ORDERS = [
  {
    id: 1,
    orderNo: "ORD-20260729-01",
    date: "2026-07-29",
    title: "자바 ORM 표준 JPA 프로그래밍",
    price: 38700,
    status: "shipping",
  },
  {
    id: 2,
    orderNo: "ORD-20260720-04",
    date: "2026-07-20",
    title: "클린 코드 (Clean Code)",
    price: 29000,
    status: "delivered",
  },
  {
    id: 3,
    orderNo: "ORD-20260715-02",
    date: "2026-07-15",
    title: "스프링 부트 실전 활용",
    price: 32000,
    status: "canceled",
  },
];

// GET /api/coupons/me 응답 형태 — MemberCouponView[]
//
// 만료된 쿠폰(예전 status: "expired")은 넣지 않는다. 백엔드가 미사용 + 미만료만
// 내려주므로 만료 항목이 화면에 도달할 경로가 없다.
export const MOCK_COUPON_STATE = [
  {
    memberCouponId: 1,
    couponId: 11,
    couponCode: "WELCOME3000",
    couponName: "신규 가입 3,000원 할인 쿠폰",
    discountAmount: 3000,
    minimumOrderAmount: 10000,
    expiresAt: "2026-08-31T23:59:59",
  },
  {
    memberCouponId: 2,
    couponId: 12,
    couponCode: "SUMMER10",
    couponName: "여름 독서 페스티벌 10% 할인",
    discountAmount: 5000,
    minimumOrderAmount: 30000,
    expiresAt: "2026-08-15T23:59:59",
  },
];

export const MOCK_RETURN_REQUESTS = [
  {
    id: 1,
    orderNo: "ORD-20260710-03",
    title: "이펙티브 자바",
    reason: "단순 변심",
    status: "processing",
  },
];

// GET /api/catalog/restock-alerts/me 응답 형태 — RestockAlertResponse[]
export const MOCK_RESTOCK_REQUESTS = [
  {
    restockAlertId: 1,
    bookId: 21,
    title: "도메인 주도 설계",
    status: "REQUESTED",
    retryCount: 0,
    requestedAt: "2026-07-22T14:03:00",
    notifiedAt: null,
    cancelledAt: null,
  },
  {
    restockAlertId: 2,
    bookId: 22,
    title: "리팩터링 2판",
    status: "REQUESTED",
    retryCount: 0,
    requestedAt: "2026-07-18T09:41:00",
    notifiedAt: null,
    cancelledAt: null,
  },
];

export const MOCK_REVIEWS = [
  {
    id: 1,
    book: "자바 ORM 표준 JPA 프로그래밍",
    rating: 5,
    date: "2026-07-29",
    content: "JPA 개념 잡기에 최고의 책입니다. 예제 코드도 잘 제공하여 학습하기 너무 좋았습니다.",
  },
  {
    id: 2,
    book: "클린 코드 (Clean Code)",
    rating: 4,
    date: "2026-07-25",
    content: "실무자 입장에서 리팩터링 기법을 실전 예제로 배울 수 있어서 좋았습니다.",
  },
  {
    id: 3,
    book: "해리 포터와 마법사의 돌",
    rating: 5,
    date: "2026-07-18",
    content: "가격도 합리적이고 배송도 빨라서 만족스러운 구매였습니다.",
  },
];
