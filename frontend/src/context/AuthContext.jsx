import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { AUTH_TOKENS_CHANGED, readTokens, writeTokens, clearTokens } from "../api/authStorage.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [tokens, setTokens] = useState(() => readTokens());

  // 토큰을 여기서만 바꾸는 게 아니다 - client.ts 의 401 인터셉터가 갱신에 실패하면
  // clearTokens() 로 직접 지운다. 그 변화를 이 state 에 반영하지 않으면 저장소는 비었는데
  // 화면만 로그인 상태로 남는다.
  useEffect(() => {
    const sync = () => setTokens(readTokens());
    window.addEventListener(AUTH_TOKENS_CHANGED, sync);
    return () => window.removeEventListener(AUTH_TOKENS_CHANGED, sync);
  }, []);

  const login = useCallback((tokenResponse) => {
    writeTokens(tokenResponse);
    setTokens(tokenResponse);
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    setTokens(null);
  }, []);

  const value = {
    isAuthenticated: Boolean(tokens?.accessToken),
    tokens,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
