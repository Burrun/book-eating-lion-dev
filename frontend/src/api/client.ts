import axios from "axios";
import type { ApiResponse } from "./types.ts";
import { readTokens, writeTokens, clearTokens } from "./authStorage.js";

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "/api";

export const apiClient = axios.create({ baseURL });

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

// 액세스 토큰이 만료되면(401) refreshToken 으로 한 번 갱신하고 원 요청을 재시도한다.
// 갱신까지 실패하면 토큰을 지운다 — clearTokens 가 이벤트를 쏘므로 AuthContext 가 즉시
// 로그아웃 상태로 바뀌고 ProtectedRoute 가 /login 으로 보낸다.
//
// 이게 없을 때 - localStorage 에 만료된 토큰이 그대로 남아 isAuthenticated 가 계속 true 라
// 화면은 로그인 상태인데 모든 API 가 401 을 뱉었다. 사용자가 눈치채고 직접 다시
// 로그인하는 수밖에 없었다.
const REFRESH_PATH = "/auth/refresh";

// 갱신은 apiClient 가 아니라 이 인스턴스로 부른다. 같은 클라이언트를 쓰면 갱신 요청이
// 또 401 일 때 자기 자신을 다시 부르며 무한 루프가 된다.
const refreshClient = axios.create({ baseURL });

// 401 이 여러 요청에서 동시에 터져도 갱신은 한 번만 한다. 나머지는 이 프로미스를
// 기다렸다가 새 토큰으로 재시도한다 - 안 그러면 refreshToken 이 동시에 여러 번
// 소비되면서 일부가 무효화된다(Cognito 는 회전 시 이전 토큰을 버린다).
let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const tokens = readTokens();
  if (!tokens?.refreshToken) return null;

  try {
    const { data } = await refreshClient.post<ApiResponse<Record<string, unknown>>>(REFRESH_PATH, {
      refreshToken: tokens.refreshToken,
    });
    const next = data?.data;
    if (!next?.accessToken) return null;

    // 응답에 refreshToken 이 없을 수 있다(Cognito 는 회전하지 않으면 안 돌려준다).
    // 그때 통째로 덮어쓰면 기존 refreshToken 을 잃어 다음 갱신이 불가능해진다.
    writeTokens({ ...tokens, ...next });
    return next.accessToken as string;
  } catch {
    return null;
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const isUnauthorized = error.response?.status === 401;

    // _retried - 재시도한 요청이 또 401 이면 갱신해도 소용없다는 뜻이니 여기서 끝낸다.
    if (!isUnauthorized || !original || original._retried) {
      if (isUnauthorized) clearTokens();
      return Promise.reject(error);
    }

    original._retried = true;
    refreshInFlight = refreshInFlight ?? refreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
    const accessToken = await refreshInFlight;

    if (!accessToken) {
      clearTokens();
      return Promise.reject(error);
    }

    const tokenType = readTokens()?.tokenType ?? "Bearer";
    original.headers.Authorization = `${tokenType} ${accessToken}`;
    return apiClient(original);
  },
);

function toApiError(body: ApiResponse<unknown> | undefined): Error {
  const error = Object.assign(
    new Error(body?.error?.message ?? body?.message ?? "API request failed"),
    {
      code: body?.error?.code,
    },
  );
  return error;
}

// ApiResponse<T> 껍데기를 벗겨 data만 반환한다.
// success: false (HTTP 200이어도 논리적으로 실패한 응답)면 error 정보로 예외를 던져
// react-query 등 호출측이 실패로 인식하게 한다. 4xx/5xx(예: 403 REVIEW_PERMISSION_REQUIRED)는
// axios가 먼저 reject하므로 별도로 잡아 같은 형태의 에러로 통일한다.
// 두 경로 모두 error.code(ApiResponse.error 의 code)를 Error.code 에 실어 호출측이
// 메시지 문자열을 파싱하지 않고 원인을 구분할 수 있게 한다.
export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const res = await promise;
    const body = res.data;
    if (!body.success) {
      throw toApiError(body);
    }
    return body.data;
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.data) {
      throw toApiError(err.response.data as ApiResponse<unknown>);
    }
    throw err;
  }
}
