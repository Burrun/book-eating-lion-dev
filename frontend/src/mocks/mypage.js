export const MOCK_PROFILE = {
  name: "홍길동",
  level: 5,
  exp: 75,
  title: "대양 사자",
  badges: [
    { type: "achievement", label: "전체 10건 달성" },
    { type: "reading", label: "독서량 41권" },
    { type: "streak", label: "7일 연속 출석" },
  ],
};

export const MOCK_FED_BOOKS = [
  { id: "book-a", title: "완독 도서 A" },
  { id: "book-b", title: "완독 도서 B" },
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
  pointBalance: 5400,
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
    content: "중고매물로 저렴하게 잘 구매했어요. 책 상태도 생각보다 깨끗해서 만족스러운 구매였습니다.",
  },
];
