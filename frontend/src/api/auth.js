import apiClient from "./client.js";
import { mockLogin } from "../mocks/auth.js";

// 참고: 기존 mypage.js/checkout.js/cart.js는 apiClient 응답(ApiResponse<T> envelope)을
// 언래핑하지 않고 그대로 반환하는 기존 패턴을 쓴다(이번 스코프 밖, 손대지 않음).
// 이 파일은 토큰 정합성이 중요해 실API 분기에서 `data.data`로 명시적으로 언래핑한다.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export async function login(email, password) {
  if (USE_MOCK) return mockLogin(email, password);
  const { data } = await apiClient.post("/auth/login", { email, password });
  return data.data;
}
