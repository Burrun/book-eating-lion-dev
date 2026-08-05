import { createContext, useCallback, useContext, useState } from "react";
import { readTokens, writeTokens, clearTokens } from "../api/authStorage.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [tokens, setTokens] = useState(() => readTokens());

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
