import { apiClient, unwrap } from "./client.ts";
import {
  MOCK_PROFILE,
  MOCK_RAG_ANSWER,
  MOCK_ORDERS,
  MOCK_COUPON_STATE,
  MOCK_RETURN_REQUESTS,
  MOCK_REVIEWS,
  mockGetLionStatus,
  mockFeedLion,
} from "../mocks/mypage.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

function mockDelay(value, ms = 400) {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

// GET /api/members/me — 회원 정보
// 응답: { id, email, name, phoneNumber, gender, birthDate, role }
//
// 레벨·EXP 는 여기 없다. 사자 상태는 별개 서비스(ai)가 소유하므로 fetchLionStatus() 를 쓴다.
export async function fetchProfile() {
  if (USE_MOCK) return mockDelay(MOCK_PROFILE);
  return unwrap(apiClient.get("/members/me"));
}

// GET /api/ai/lion/me — 내 사자 상태
// 응답: { exp, level, growthStage, fedBookCount }
//
// 🔴 exp 는 누적값이다(레벨업해도 차감되지 않는다). 백엔드가 level = 1 + exp/100 으로
// 계산하므로 exp 120 이면 level 2 다. 진행바처럼 0~100 이 필요한 자리에서는 exp % 100 을 쓴다.
//
// 조회는 사자를 만들지 않는다. 한 번도 안 먹인 사용자는 기본값(exp 0 / level 1 / BABY)이 온다.
export async function fetchLionStatus() {
  if (USE_MOCK) return mockDelay(mockGetLionStatus());
  return unwrap(apiClient.get("/ai/lion/me"));
}

// POST /api/ai/lion/feed — 완독한 책 먹이기(EXP만 오른다).
// 응답은 GET /api/ai/lion/me 와 같은 형태(LionStatus)다. exp는 멱등이라 같은 책을 다시
// 먹여도 중복으로 오르지 않는다.
//
// 🔴 여기서 검색 인덱스는 건드리지 않는다. 책 본문 벡터는 관리자 배치가 이미 넣어뒀고,
// 사용자가 쓴 메모는 애초에 RAG 근거로 쓰지 않는다.
export async function feedLion(bookId) {
  if (USE_MOCK) return mockDelay(mockFeedLion(bookId));
  return unwrap(apiClient.post("/ai/lion/feed", { bookId }));
}

// POST /api/ai/lion/ask — 구매한 책 본문에 대한 RAG 질의(내가 쓴 메모는 근거에서 빠진다)
// 🔴 요청 필드는 question 이 아니라 query 다(AskRequest).
// 응답: { mode, grounded, answer, citations[] } — 근거를 못 찾아도 200 이고
// grounded: false 에 answer 는 고정 문구다.
//
// 🔴 mode를 생략하면 서버 QueryRouter가 "왜/이유/설명/알려줘" 같은 키워드가 없는 질문을
// 전부 search로 내려버린다 — 그러면 LLM을 안 부르고 answer가 "구매한 책에서 근거
// N곳을 찾았습니다"라는 정형 문구로만 온다(citations 유사도%만 의미 있어 보이는 이유).
// "사자에게 물어보기"는 대화형 UX라 항상 answer 모드로 명시해 실제 문장 답변을 받는다.
// bookIds 를 주면 그 책들로만 검색을 좁힌다 — 서버가 구매 목록과 교집합을 내므로(WikiRagService
// .allowedBooks) 안 산 책을 실어보내도 새지 않는다. 비었으면 아예 안 보낸다: null/[] 둘 다
// "구매한 책 전체"로 해석되지만, 필드 자체를 빼는 쪽이 계약상 기본값과 같아 오해가 없다.
export async function askLion(question, bookIds) {
  if (USE_MOCK) return mockDelay(MOCK_RAG_ANSWER, 800);
  return unwrap(
    apiClient.post("/ai/lion/ask", {
      query: question,
      mode: "answer",
      ...(bookIds?.length ? { bookIds } : {}),
    }),
  );
}

// 🚧 백엔드 미구현. /api/orders (목록)는 있으나 마이페이지 전용 형태가 필요한지 미확정.
// docs/frontend/mypage-unimplemented.md §2
export async function fetchOrders() {
  if (USE_MOCK) return mockDelay(MOCK_ORDERS);
  return unwrap(apiClient.get("/mypage/orders"));
}

// GET /api/coupons/me — 미사용 + 미만료 쿠폰만 온다(만료/사용분은 조용히 빠진다).
// 응답: [{ memberCouponId, couponId, couponCode, couponName, discountAmount,
//          minimumOrderAmount, expiresAt }]
export async function fetchCoupons() {
  if (USE_MOCK) return mockDelay(MOCK_COUPON_STATE);
  return unwrap(apiClient.get("/coupons/me"));
}

// 🚧 백엔드 미구현. 반품 신청(POST /api/orders/{id}/return)은 있으나 목록 조회가 없다.
// docs/frontend/mypage-unimplemented.md §2
export async function fetchReturnRequests() {
  if (USE_MOCK) return mockDelay(MOCK_RETURN_REQUESTS);
  return unwrap(apiClient.get("/mypage/returns"));
}

// GET /api/catalog/reviews/me — 현재 로그인 회원이 작성한 리뷰 최신순 조회
export async function fetchReviews() {
  if (USE_MOCK) return mockDelay(MOCK_REVIEWS);
  const reviews = await unwrap(apiClient.get("/catalog/reviews/me"));
  return reviews.map((review) => ({
    id: review.id,
    bookId: review.bookId,
    book: review.bookTitle,
    rating: review.rating,
    content: review.content,
    date: review.createdAt.slice(0, 10),
  }));
}
