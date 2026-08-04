import apiClient from "./client.js";
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
  const { data } = await apiClient.get("/members/me");
  return data;
}

export async function fetchFedBooks() {
  if (USE_MOCK) return mockDelay(MOCK_FED_BOOKS);
  const { data } = await apiClient.get("/mypage/lion/feedable-books");
  return data;
}

export async function fetchReadingNotes() {
  if (USE_MOCK) return mockDelay(MOCK_READING_NOTES);
  const { data } = await apiClient.get("/mypage/reading-notes");
  return data;
}

export async function askLion(question) {
  if (USE_MOCK) return mockDelay(MOCK_RAG_ANSWER, 800);
  const { data } = await apiClient.post("/mypage/rag/ask", { question });
  return data;
}

export async function fetchOrders() {
  if (USE_MOCK) return mockDelay(MOCK_ORDERS);
  const { data } = await apiClient.get("/mypage/orders");
  return data;
}

export async function fetchCoupons() {
  if (USE_MOCK) return mockDelay(MOCK_COUPON_STATE);
  const { data } = await apiClient.get("/mypage/coupons");
  return data;
}

export async function fetchReturnRequests() {
  if (USE_MOCK) return mockDelay(MOCK_RETURN_REQUESTS);
  const { data } = await apiClient.get("/mypage/returns");
  return data;
}

export async function fetchRestockRequests() {
  if (USE_MOCK) return mockDelay(MOCK_RESTOCK_REQUESTS);
  const { data } = await apiClient.get("/mypage/restock-requests");
  return data;
}

export async function fetchReviews() {
  if (USE_MOCK) return mockDelay(MOCK_REVIEWS);
  const { data } = await apiClient.get("/mypage/reviews");
  return data;
}
