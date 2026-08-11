import { apiClient, unwrap } from "./client.ts";
import {
  MOCK_PROFILE,
  MOCK_FED_BOOKS,
  MOCK_READING_NOTES,
  MOCK_RAG_ANSWER,
  MOCK_ORDERS,
  MOCK_COUPON_STATE,
  MOCK_RETURN_REQUESTS,
  MOCK_RESTOCK_REQUESTS,
  MOCK_REVIEWS,
} from "../mocks/mypage.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

function mockDelay(value, ms = 400) {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

export async function fetchProfile() {
  if (USE_MOCK) return mockDelay(MOCK_PROFILE);
  return unwrap(apiClient.get("/members/me"));
}

export async function fetchFedBooks() {
  if (USE_MOCK) return mockDelay(MOCK_FED_BOOKS);
  return unwrap(apiClient.get("/mypage/lion/feedable-books"));
}

export async function fetchReadingNotes() {
  if (USE_MOCK) return mockDelay(MOCK_READING_NOTES);
  return unwrap(apiClient.get("/mypage/reading-notes"));
}

export async function askLion(question) {
  if (USE_MOCK) return mockDelay(MOCK_RAG_ANSWER, 800);
  return unwrap(apiClient.post("/mypage/rag/ask", { question }));
}

export async function fetchOrders() {
  if (USE_MOCK) return mockDelay(MOCK_ORDERS);
  return unwrap(apiClient.get("/mypage/orders"));
}

export async function fetchCoupons() {
  if (USE_MOCK) return mockDelay(MOCK_COUPON_STATE);
  return unwrap(apiClient.get("/mypage/coupons"));
}

export async function fetchReturnRequests() {
  if (USE_MOCK) return mockDelay(MOCK_RETURN_REQUESTS);
  return unwrap(apiClient.get("/mypage/returns"));
}

export async function fetchRestockRequests() {
  if (USE_MOCK) return mockDelay(MOCK_RESTOCK_REQUESTS);
  return unwrap(apiClient.get("/mypage/restock-requests"));
}

export async function fetchReviews() {
  if (USE_MOCK) return mockDelay(MOCK_REVIEWS);
  return unwrap(apiClient.get("/mypage/reviews"));
}
