import axios from "axios";
import type { ApiResponse } from "./types.ts";
import { readTokens } from "./authStorage.js";

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api",
});

// 인증은 이 인터셉터 하나로 끝난다. authStorage(localStorage)에 저장된 토큰을
// 매 요청에 실어 보내고, 백엔드는 JWT 의 sub 클레임으로 회원을 식별한다.
//
// 예전에는 X-Member-Id 헤더로 사용자를 넘겼으나 지금은 어느 서비스도 그 헤더를 읽지
// 않는다 — catalog 는 CatalogMemberIdentity, 나머지는 SecurityUtils.currentMemberSub()
// 가 모두 jwt.getSubject() 를 쓴다.
apiClient.interceptors.request.use((config) => {
  const tokens = readTokens();
  if (tokens?.accessToken) {
    config.headers.Authorization = `${tokens.tokenType ?? "Bearer"} ${tokens.accessToken}`;
  }
  return config;
});

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
