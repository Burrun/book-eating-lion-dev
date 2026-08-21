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
  role: "ADMIN",
};

// mock 전용. 대응 API 가 없다.
export const MOCK_BADGES = [
  { type: "achievement", label: "전체 10건 달성" },
  { type: "reading", label: "독서량 41권" },
  { type: "streak", label: "7일 연속 출석" },
];

// "streak" 배지의 불꽃 연출 임계값(3일 이상/7일 이상) 판단에만 쓰인다. 배지 라벨 텍스트와 맞춰둘 것.
export const MOCK_STREAK_COUNT = 7;

// GET /api/ai/lion/me, POST /api/ai/lion/feed 응답 형태 — LionStatus.
//
// 🔴 exp 는 누적값이다. 백엔드가 level = 1 + exp/100 으로 계산하므로 이 둘은 항상 맞아야 한다.
// growthStage 는 백엔드 GrowthStage.fromLevel 과 동일한 구간으로 계산한다
// (level<=2 BABY, level<=4 CUB, 그 이상 ADULT — LionCharacter.getLionTier 와도 일치해야 한다).
//
// 실API 와 마찬가지로 상태가 유지되도록 모듈 스코프 mutable 값으로 관리한다(mocks/cards.ts와
// 동일 패턴) — 예전에는 이 값이 고정 상수라 먹여도 서버(mock) 쪽 값이 바뀌지 않았다.
function growthStageOf(level) {
  if (level <= 2) return "BABY";
  if (level <= 4) return "CUB";
  return "ADULT";
}

// 120 → 이미 3권을 먹인 것으로 시작(level 2, BABY).
let lionState = { exp: 120, level: 2, growthStage: growthStageOf(2), fedBookCount: 3 };
const fedBookIds = new Set();

export function mockGetLionStatus() {
  return lionState;
}

// 계약과 동일하게 같은 책을 다시 먹여도 exp 가 중복으로 오르지 않는다(멱등).
export function mockFeedLion(bookId) {
  if (!fedBookIds.has(bookId)) {
    fedBookIds.add(bookId);
    const exp = lionState.exp + 40; // Lion.EXP_PER_FEED
    const level = 1 + Math.floor(exp / 100); // Lion.EXP_PER_LEVEL
    lionState = {
      exp,
      level,
      growthStage: growthStageOf(level),
      fedBookCount: lionState.fedBookCount + 1,
    };
  }
  return lionState;
}

// "먹일 수 있는 메모" 목록은 이제 mocks/bookMemo.ts(GET /api/catalog/members/me/memos/feedable)
// 소관이다 — LionFeedingCard가 그리는 카드가 책이 아니라 완독 후 작성한 메모로 바뀌었다.

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
