import axios from "axios";
import type { ApiResponse } from "./types.ts";
import { readTokens } from "./authStorage.js";

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api",
});

// BOO-23 로그인 연동: authStorage(localStorage)에 저장된 토큰을 매 요청마다 자동으로 실어 보낸다.
apiClient.interceptors.request.use((config) => {
  const tokens = readTokens();
  if (tokens?.accessToken) {
    config.headers.Authorization = `${tokens.tokenType ?? "Bearer"} ${tokens.accessToken}`;
  }
  return config;
});

// 백엔드는 아직 로그인 연동 전이라 X-Member-Id 헤더로 사용자를 식별한다.
// (리뷰 작성/삭제, 찜 추가/삭제, /members/me/* 에서 요구)
// JWT(Cognito) 로그인이 프론트에 붙으면 accessToken 쪽으로 대체한다.
export function setMemberId(memberId: number | null) {
  if (memberId === null) delete apiClient.defaults.headers.common["X-Member-Id"];
  else apiClient.defaults.headers.common["X-Member-Id"] = String(memberId);
}

export function setAccessToken(token: string | null) {
  if (token === null) delete apiClient.defaults.headers.common["Authorization"];
  else apiClient.defaults.headers.common["Authorization"] = `Bearer ${token}`;
}

// ApiResponse<T> 껍데기를 벗겨 data만 반환한다.
// success: false (HTTP 200이어도 논리적으로 실패한 응답)면 error 정보로 예외를 던져
// react-query 등 호출측이 실패로 인식하게 한다.
export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const res = await promise;
  const body = res.data;
  if (!body.success) {
    throw new Error(body.error?.message ?? body.message ?? "API request failed");
  }
  return body.data;
}
