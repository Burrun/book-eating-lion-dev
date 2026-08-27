const STORAGE_KEY = "auth_tokens";

// 토큰이 바뀐 걸 React 밖에서도 알릴 수 있게 하는 이벤트.
//
// 필요한 이유 - 토큰 만료 처리는 axios 인터셉터(client.ts)가 한다. 거기는 컴포넌트가
// 아니라서 useAuth().logout() 을 못 부른다. AuthContext 는 마운트 시점에 localStorage 를
// 한 번 읽고 그 값을 useState 에 들고 있으므로, 인터셉터가 localStorage 만 비우면
// 화면은 계속 로그인 상태로 남는다. 그래서 저장소가 바뀔 때마다 이 이벤트를 쏘고
// AuthContext 가 받아서 자기 state 를 다시 맞춘다.
export const AUTH_TOKENS_CHANGED = "auth-tokens-changed";

function notifyChanged() {
  window.dispatchEvent(new Event(AUTH_TOKENS_CHANGED));
}

export function readTokens() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function writeTokens(tokenResponse) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokenResponse));
  notifyChanged();
}

export function clearTokens() {
  localStorage.removeItem(STORAGE_KEY);
  notifyChanged();
}

export function isLoggedIn() {
  return Boolean(readTokens()?.accessToken);
}
