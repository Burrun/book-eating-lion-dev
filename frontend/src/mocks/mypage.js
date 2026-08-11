export const MOCK_PROFILE = {
  name: "홍길동",
  level: 1,
  exp: 20,
  badges: [
    { type: "achievement", label: "전체 10건 달성" },
    { type: "reading", label: "독서량 41권" },
    { type: "streak", label: "7일 연속 출석" },
  ],
  // "streak" 배지의 불꽃 연출 임계값(3일 이상/7일 이상) 판단에만 쓰인다. 배지 라벨 텍스트와 맞춰둘 것.
  streakCount: 7,
};

// exp 합계는 일부러 380으로 맞췄다: 시작 EXP 20 + 380 = 400 = 정확히 레벨 4번 오름(Lv1→Lv5).
// 다 먹이면 Lv1 아기 사자 → Lv5 대양 사자(왕관)까지 전 구간을 확인할 수 있다.
export const MOCK_FED_BOOKS = [
  { id: "book-1", title: "클린 코드", genre: "IT/개발", exp: 45 },
  { id: "book-2", title: "해리 포터와 마법사의 돌", genre: "소설", exp: 40 },
  { id: "book-3", title: "역행자", genre: "자기계발", exp: 50 },
  { id: "book-4", title: "언어의 온도", genre: "에세이", exp: 35 },
  { id: "book-5", title: "부의 추월차선", genre: "경제", exp: 55 },
  { id: "book-6", title: "사피엔스", genre: "인문", exp: 60 },
  { id: "book-7", title: "미드나잇 라이브러리", genre: "소설", exp: 45 },
  { id: "book-8", title: "이펙티브 자바", genre: "IT/개발", exp: 50 },
];

export const MOCK_READING_NOTES = [
  { id: 1, book: "클린 코드", quote: "“단순함은 모든 것의 시작이다.”" },
  { id: 2, book: "자바 ORM 표준 JPA 프로그래밍", quote: "“영속성 컨텍스트는 1차 캐시를 제공한다.”" },
];

export const MOCK_RAG_ANSWER = {
  text: "[자바 ORM 표준 JPA 프로그래밍] 리뷰 노트에서 '캡슐화와 접근 제어자' 구절을 찾았습니다.",
  similarity: 92,
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

export const MOCK_COUPON_STATE = {
  coupons: [
    { id: 1, label: "신규 가입 3,000원 할인 쿠폰", expiresAt: "2026-08-31", status: "available" },
    { id: 2, label: "여름 독서 페스티벌 10% 할인", expiresAt: "2026-08-15", status: "available" },
    { id: 3, label: "생일 축하 5,000원 쿠폰", expiresAt: "2026-06-30", status: "expired" },
  ],
};

export const MOCK_RETURN_REQUESTS = [
  { id: 1, orderNo: "ORD-20260710-03", title: "이펙티브 자바", reason: "단순 변심", status: "processing" },
];

export const MOCK_RESTOCK_REQUESTS = [
  { id: 1, title: "도메인 주도 설계", requestedAt: "2026-07-22" },
  { id: 2, title: "리팩터링 2판", requestedAt: "2026-07-18" },
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
