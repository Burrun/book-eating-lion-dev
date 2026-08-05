const STORAGE_KEY = "auth_tokens";

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
}

export function clearTokens() {
  localStorage.removeItem(STORAGE_KEY);
}

export function isLoggedIn() {
  return Boolean(readTokens()?.accessToken);
}
