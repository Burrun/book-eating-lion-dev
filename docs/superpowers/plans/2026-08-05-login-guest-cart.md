# 로그인 화면 + 비회원 장바구니 허용 흐름 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 페이지를 만들고, 비로그인 상태에서도 장바구니 담기가 가능하며 결제/마이페이지 진입 시점에만 로그인을 요구하는 흐름(BOO-23)을 구현한다.

**Architecture:** localStorage 기반 토큰 저장소(`authStorage.js`)를 axios 인터셉터와 React `AuthContext`가 공유해서 읽는다. `api/cart.js`는 로그인 여부에 따라 `localStorage["guest_cart"]` 또는 서버(mock 포함) 분기로 내부에서 갈라지되, 컴포넌트에는 `getCart/addToCart/updateQuantity/removeFromCart` 단일 인터페이스만 노출한다. `ProtectedRoute`가 `/checkout`, `/mypage`만 감싸 비로그인 접근을 `/login`으로 리다이렉트한다.

**Tech Stack:** React 18, react-router-dom v7, axios, framer-motion, lucide-react (전부 기존 의존성, 신규 라이브러리 설치 없음).

**Design reference:** `docs/superpowers/specs/2026-08-05-login-guest-cart-design.md`

## Global Constraints

- 브랜치는 이미 생성됨: `feature/BOO-23-login-ui` (모든 작업은 이 브랜치에서 진행).
- 커밋 메시지는 `feat: <설명> (BOO-23)` 형식. Claude/AI 관련 문구를 커밋 메시지에 넣지 않는다.
- `alert()` 사용 금지 — 에러는 반드시 `useToast()`의 `toast.error(...)`로 표시한다.
- 새 라이브러리를 설치하지 않는다 — 이 계획의 모든 작업은 `package.json`에 이미 있는 의존성만 사용한다.
- API 필드명은 확정된 명세 그대로 사용한다(임의 추측 금지):
  - `POST /api/auth/login` 요청 `{email, password}` → 응답 `TokenResponse{accessToken, refreshToken, tokenType, expiresIn}`
  - `GET /api/cart` → `{items: [{cartItemId, bookId, title, coverImageUrl, price, quantity}], totalPrice}`
  - `POST /api/cart` 요청 `{bookId, quantity}`
  - `PATCH /api/cart/{cartItemId}` 요청 `{quantity}`
  - `DELETE /api/cart/{cartItemId}`
  - `GET /api/books/{bookId}` → `BookDetailResponse{id, title, author, publisher, isbn, category, price, stockQuantity, coverImageUrl, description, saleStatus, publishedDate, createdAt, updatedAt}`
  - 백엔드 `ApiResponse<T>` envelope: `{success, message, data, error}` — 이 계획에서 새로 만드는 파일의 실API 분기는 `data.data`로 언래핑한다(기존 `mypage.js`/`checkout.js`/기존 `cart.js`는 언래핑하지 않는 기존 패턴이며 이번 스코프에서 손대지 않는다).
- 디자인 토큰은 기존 값을 그대로 재사용한다: `--color-forest:#1B3B36`, `--color-honey:#F2A93B`, `--color-paper:#FBF6EC`, `--color-coral:#E8563F`, `--color-ink:#2D2A26`, `font-display`(GmarketSansBold), 본문(Pretendard). 새 클래스/색상 추가 없음.
- 프론트엔드에는 테스트 러너(vitest 등)가 아직 없다. 이 계획의 모든 검증은 `npm run dev`(frontend 디렉터리에서 실행, `VITE_USE_MOCK=true`가 기본값) + 브라우저 수동 확인(devtools의 Application 탭에서 `localStorage` 직접 확인/조작 포함)으로 진행한다. 새 테스트 프레임워크는 설치하지 않는다.
- 백엔드 cart 모듈은 아직 없다. 실API 분기 코드는 작성하되 동작 확인은 불가하므로, 실API 분기마다 `// TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작` 주석을 남긴다.

---

### Task 1: 토큰 저장소 공용 모듈 — `authStorage.js`

**Files:**
- Create: `frontend/src/api/authStorage.js`

**Interfaces:**
- Produces: `readTokens(): TokenResponse | null`, `writeTokens(tokenResponse: TokenResponse): void`, `clearTokens(): void`, `isLoggedIn(): boolean` — 이후 모든 태스크(client.js 인터셉터, AuthContext, cart.js)가 이 4개 함수로만 localStorage의 `auth_tokens` 키에 접근한다. 이 파일 밖에서 `localStorage.getItem("auth_tokens")` / `setItem` / `removeItem`을 직접 호출하지 않는다.

- [ ] **Step 1: 파일 작성**

```js
// frontend/src/api/authStorage.js
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
```

- [ ] **Step 2: 수동 확인**

`frontend` 디렉터리에서 `npm run dev` 실행 후 브라우저에서 아무 페이지나 열고 devtools 콘솔에서:

```js
localStorage.setItem("auth_tokens", JSON.stringify({ accessToken: "abc", refreshToken: "r", tokenType: "Bearer", expiresIn: 3600 }));
```

devtools Application 탭 → Local Storage에서 `auth_tokens` 키가 위 값으로 저장되어 있는지 확인한다(이 시점엔 아직 `isLoggedIn()`을 호출할 UI가 없으므로 저장 자체만 확인). 확인 후 `localStorage.removeItem("auth_tokens")`로 정리한다.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/api/authStorage.js
git commit -m "feat: 토큰 저장소 공용 모듈 추가 (BOO-23)"
```

---

### Task 2: `AuthContext` — 전역 로그인 상태

**Files:**
- Create: `frontend/src/context/AuthContext.jsx`

**Interfaces:**
- Consumes: `readTokens`, `writeTokens`, `clearTokens` from `../api/authStorage.js` (Task 1)
- Produces: `AuthProvider({children})` 컴포넌트, `useAuth()` 훅 → `{ isAuthenticated: boolean, tokens: TokenResponse | null, login(tokenResponse): void, logout(): void }`. 이후 `ProtectedRoute`, `Login.jsx`, `Header.jsx`가 `useAuth()`를 사용한다.

- [ ] **Step 1: 파일 작성**

```jsx
// frontend/src/context/AuthContext.jsx
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
```

- [ ] **Step 2: 수동 확인**

아직 `App.jsx`에 연결하지 않았으므로 이 태스크만으로는 브라우저에서 직접 확인할 수 없다. 대신 파일이 문법 오류 없이 빌드되는지만 확인한다:

```bash
cd frontend && npm run build
```

Expected: 빌드 성공(에러 없음). `AuthContext.jsx`가 아직 아무 데서도 import되지 않아 tree-shaking으로 빠지더라도 문법 에러는 빌드 단계에서 잡힌다.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/context/AuthContext.jsx
git commit -m "feat: AuthContext 추가 (BOO-23)"
```

---

### Task 3: `api/client.js` — Authorization 헤더 자동 첨부

**Files:**
- Modify: `frontend/src/api/client.js`

**Interfaces:**
- Consumes: `readTokens` from `../api/authStorage.js` (Task 1)
- Produces: 기존 `export default apiClient` 그대로 유지(시그니처 변경 없음) — 모든 기존 `apiClient.get/post/...` 호출부는 수정 불필요.

- [ ] **Step 1: 파일 전체 교체**

```js
// frontend/src/api/client.js
import axios from "axios";
import { readTokens } from "./authStorage.js";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api",
});

apiClient.interceptors.request.use((config) => {
  const tokens = readTokens();
  if (tokens?.accessToken) {
    config.headers.Authorization = `${tokens.tokenType ?? "Bearer"} ${tokens.accessToken}`;
  }
  return config;
});

export default apiClient;
```

- [ ] **Step 2: 수동 확인**

`npm run dev` 실행 후 브라우저에서 `/mypage` 접속(아직 ProtectedRoute가 없으므로 그대로 열림). devtools Network 탭에서 발생하는 요청(mock 모드라 실제 네트워크 요청은 없을 수 있음 — 이 경우 devtools 콘솔에서 아래로 직접 검증):

```js
localStorage.setItem("auth_tokens", JSON.stringify({ accessToken: "test-token", tokenType: "Bearer" }));
```

이후 페이지를 새로고침하지 않고, 콘솔에서 아무 axios 요청이 발생하는 조작을 하기 어려우므로, 대신 `VITE_USE_MOCK=false`로 잠깐 로컬 실행해 실제 네트워크 탭에서 `Authorization: Bearer test-token` 헤더가 붙는지 확인해도 되고, 간단히 다음 코드로 인터셉터 로직만 검증한다: 콘솔에서 `JSON.parse(localStorage.getItem("auth_tokens"))`가 `{accessToken:"test-token", tokenType:"Bearer"}`를 반환하는지 확인(= `readTokens()`가 읽을 데이터가 정상 저장돼 있음을 확인). 이 태스크는 Task 6(cart.js 실API 분기)에서 실제 헤더 첨부 여부를 함께 재확인한다. 확인 후 `localStorage.removeItem("auth_tokens")`.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/api/client.js
git commit -m "feat: Authorization 헤더 자동 첨부 인터셉터 추가 (BOO-23)"
```

---

### Task 4: 로그인 API — `mocks/auth.js` + `api/auth.js` (login만)

**Files:**
- Create: `frontend/src/mocks/auth.js`
- Create: `frontend/src/api/auth.js`

**Interfaces:**
- Consumes: `apiClient` from `./client.js` (Task 3)
- Produces: `login(email, password): Promise<TokenResponse>` — mock 모드에서 `password === "wrong"`이면 reject, 그 외엔 고정 `TokenResponse`로 resolve. `mergeCart`는 Task 7에서 같은 파일에 추가된다(이 태스크에서는 만들지 않음).

- [ ] **Step 1: `mocks/auth.js` 작성**

```js
// frontend/src/mocks/auth.js
export const MOCK_TOKEN_RESPONSE = {
  accessToken: "mock-access-token",
  refreshToken: "mock-refresh-token",
  tokenType: "Bearer",
  expiresIn: 3600,
};

export function mockLogin(email, password) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (password === "wrong") {
        reject({
          response: {
            data: {
              success: false,
              message: "이메일 또는 비밀번호가 올바르지 않습니다.",
              error: { code: "INVALID_CREDENTIALS", message: "이메일 또는 비밀번호가 올바르지 않습니다." },
            },
          },
        });
        return;
      }
      resolve(MOCK_TOKEN_RESPONSE);
    }, 400);
  });
}
```

- [ ] **Step 2: `api/auth.js` 작성 (login만)**

```js
// frontend/src/api/auth.js
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
```

- [ ] **Step 3: 수동 확인**

`npm run dev` 실행 후 브라우저 devtools 콘솔에서(Vite dev 서버 페이지 아무 곳에서나, 모듈은 아직 어디서도 import되지 않았으므로 콘솔에서 직접 import는 안 되고 대신 build로 문법만 확인):

```bash
cd frontend && npm run build
```

Expected: 빌드 성공. `login()`/`mockLogin()`의 실제 동작 확인은 Task 8(Login.jsx)에서 UI를 통해 진행한다.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/mocks/auth.js frontend/src/api/auth.js
git commit -m "feat: 로그인 API 및 mock 추가 (BOO-23)"
```

---

### Task 5: `mocks/cart.js` — mock 서버 장바구니 + 게스트용 책 카탈로그

**Files:**
- Modify: `frontend/src/mocks/cart.js`

**Interfaces:**
- Produces: `MOCK_CART_ITEMS`(형태 변경: `id`→`cartItemId`+`bookId` 추가), `MOCK_CART_BENEFITS`(변경 없음), `MOCK_BOOK_CATALOG: {[bookId]: {bookId, title, price, coverImageUrl}}`, `mockGetCart(): {items, totalPrice}`, `mockAddToCart(bookId, quantity): item`, `mockUpdateQuantity(cartItemId, quantity): item`, `mockRemoveFromCart(cartItemId): void` — Task 6(`api/cart.js`)이 이 5개 함수/상수를 그대로 가져다 쓴다.

- [ ] **Step 1: 파일 전체 교체**

```js
// frontend/src/mocks/cart.js
export const MOCK_CART_ITEMS = [
  {
    cartItemId: 1,
    bookId: 1,
    title: "자바 ORM 표준 JPA 프로그래밍",
    option: "신간 · 개정판",
    price: 38700,
    shippingFee: 3000,
    quantity: 1,
    coverImageUrl: null,
  },
  {
    cartItemId: 2,
    bookId: 2,
    title: "클린 코드 (Clean Code)",
    option: "베스트셀러",
    price: 29000,
    shippingFee: 3000,
    quantity: 1,
    coverImageUrl: null,
  },
  {
    cartItemId: 3,
    bookId: 3,
    title: "해리 포터와 마법사의 돌",
    option: "중고 직거래 증정",
    condition: "A",
    price: 12000,
    shippingFee: 3000,
    quantity: 1,
    coverImageUrl: null,
  },
];

export const MOCK_CART_BENEFITS = {
  availableCoupon: { label: "신규 가입 3,000원 할인 쿠폰", discount: 3000 },
  availablePoints: 5400,
};

// 비회원 장바구니 화면 표시용 — 실제로는 GET /api/books/{bookId}에서 내려오는 정보를 대신한다.
export const MOCK_BOOK_CATALOG = {
  1: { bookId: 1, title: "자바 ORM 표준 JPA 프로그래밍", price: 38700, coverImageUrl: null },
  2: { bookId: 2, title: "클린 코드 (Clean Code)", price: 29000, coverImageUrl: null },
  3: { bookId: 3, title: "해리 포터와 마법사의 돌", price: 12000, coverImageUrl: null },
};

// 로그인 상태의 mock "서버" 장바구니 — 백엔드 cart 모듈이 없어 메모리에서 흉내낸다.
// 페이지를 새로고침하면 MOCK_CART_ITEMS 초기값으로 리셋된다.
let mockServerCart = MOCK_CART_ITEMS.map((item) => ({ ...item }));
let nextMockCartItemId = mockServerCart.length + 1;

export function mockGetCart() {
  const totalPrice = mockServerCart.reduce((sum, item) => sum + item.price * item.quantity, 0);
  return { items: mockServerCart.map((item) => ({ ...item })), totalPrice };
}

export function mockAddToCart(bookId, quantity) {
  const existing = mockServerCart.find((item) => item.bookId === bookId);
  if (existing) {
    existing.quantity += quantity;
    return { ...existing };
  }
  const book = MOCK_BOOK_CATALOG[bookId] ?? { bookId, title: `도서 #${bookId}`, price: 0, coverImageUrl: null };
  const item = {
    cartItemId: nextMockCartItemId++,
    bookId,
    title: book.title,
    coverImageUrl: book.coverImageUrl,
    price: book.price,
    quantity,
  };
  mockServerCart.push(item);
  return { ...item };
}

export function mockUpdateQuantity(cartItemId, quantity) {
  const found = mockServerCart.find((item) => item.cartItemId === cartItemId);
  if (!found) throw new Error(`mock cart item not found: ${cartItemId}`);
  found.quantity = quantity;
  return { ...found };
}

export function mockRemoveFromCart(cartItemId) {
  mockServerCart = mockServerCart.filter((item) => item.cartItemId !== cartItemId);
}
```

- [ ] **Step 2: 수동 확인**

```bash
cd frontend && npm run build
```

Expected: 빌드 성공. `mockGetCart`/`mockAddToCart` 등의 실제 동작 확인은 Task 6에서 `getCart()`를 통해 진행한다.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/mocks/cart.js
git commit -m "feat: mock 서버 장바구니 및 책 카탈로그 추가 (BOO-23)"
```

---

### Task 6: `api/cart.js` 재설계 — 게스트/서버 분기

**Files:**
- Modify: `frontend/src/api/cart.js`

**Interfaces:**
- Consumes: `isLoggedIn` from `./authStorage.js` (Task 1); `apiClient` from `./client.js` (Task 3); `MOCK_CART_BENEFITS, MOCK_BOOK_CATALOG, mockGetCart, mockAddToCart, mockUpdateQuantity, mockRemoveFromCart` from `../mocks/cart.js` (Task 5).
- Produces:
  - `getCart(): Promise<{items: [{cartItemId, bookId, title, coverImageUrl, price, quantity}], totalPrice}>`
  - `addToCart(bookId, quantity): Promise<void|item>`
  - `updateQuantity(cartItemId, quantity): Promise<void|item>`
  - `removeFromCart(cartItemId): Promise<void>`
  - `getGuestCartItems(): [{bookId, quantity}]`
  - `clearGuestCart(): void`
  - `fetchCartItems(): Promise<[{id, bookId, title, price, quantity, coverUrl, shippingFee, option?, condition?}]>` (기존 시그니처 유지, `Cart.jsx`가 그대로 사용)
  - `fetchCartBenefits(): Promise<MOCK_CART_BENEFITS 형태>` (기존과 동일, 변경 없음)
  - Task 7(`mergeCart`)이 `getCart`/`addToCart`/`updateQuantity`를, Task 8(`Login.jsx`)이 `getGuestCartItems`/`clearGuestCart`를, Task 11(`Cart.jsx`)이 `updateQuantity`/`removeFromCart`를 가져다 쓴다.

- [ ] **Step 1: 파일 전체 교체**

```js
// frontend/src/api/cart.js
import apiClient from "./client.js";
import { isLoggedIn } from "./authStorage.js";
import {
  MOCK_CART_BENEFITS,
  MOCK_BOOK_CATALOG,
  mockGetCart,
  mockAddToCart,
  mockUpdateQuantity,
  mockRemoveFromCart,
} from "../mocks/cart.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const GUEST_CART_KEY = "guest_cart";

// 참고: 기존 cart.js/mypage.js/checkout.js는 ApiResponse<T> envelope({success,message,data,error})을
// 언래핑하지 않고 그대로 반환하는 기존 패턴을 쓴다(이번 스코프 밖, 손대지 않음).
// 이 파일의 실API 분기(로그인 상태)는 `data.data`로 명시적으로 언래핑한다.

function readGuestCart() {
  try {
    const raw = localStorage.getItem(GUEST_CART_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function writeGuestCart(items) {
  localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
}

export function getGuestCartItems() {
  return readGuestCart();
}

export function clearGuestCart() {
  localStorage.removeItem(GUEST_CART_KEY);
}

// 게스트 카트는 book 상세(title/price)를 갖지 않으므로 화면 표시용으로 book API를 조회해 합성한다.
// mock 모드에선 실제 book API 대신 MOCK_BOOK_CATALOG로 대체한다.
async function fetchBookSummary(bookId) {
  if (USE_MOCK) {
    return MOCK_BOOK_CATALOG[bookId] ?? { bookId, title: `도서 #${bookId}`, price: 0, coverImageUrl: null };
  }
  const { data } = await apiClient.get(`/books/${bookId}`);
  const book = data.data;
  return { bookId: book.id, title: book.title, price: book.price, coverImageUrl: book.coverImageUrl };
}

// 게스트 카트는 "한 책당 한 줄만 존재한다"는 전제 하에 서버 PK가 없는 bookId를
// cartItemId 대용으로 재사용한다. 게스트 카트에 같은 책이 여러 줄로 존재할 수 있게 되면
// 이 가정이 깨지므로, addToGuestCart는 항상 기존 줄의 수량을 합산하고 새 줄을 만들지 않는다.
async function getGuestCart() {
  const localItems = readGuestCart();
  const items = await Promise.all(
    localItems.map(async ({ bookId, quantity }) => {
      const book = await fetchBookSummary(bookId);
      return {
        cartItemId: bookId,
        bookId,
        title: book.title,
        coverImageUrl: book.coverImageUrl,
        price: book.price,
        quantity,
      };
    })
  );
  const totalPrice = items.reduce((sum, item) => sum + item.price * item.quantity, 0);
  return { items, totalPrice };
}

function addToGuestCart(bookId, quantity) {
  const items = readGuestCart();
  const existing = items.find((item) => item.bookId === bookId);
  if (existing) {
    existing.quantity += quantity;
  } else {
    items.push({ bookId, quantity });
  }
  writeGuestCart(items);
}

// cartItemId 파라미터는 게스트 모드에서 bookId와 동일한 값이다(getGuestCart 참고).
function updateGuestCartQuantity(cartItemId, quantity) {
  const items = readGuestCart();
  const existing = items.find((item) => item.bookId === cartItemId);
  if (!existing) return;
  existing.quantity = quantity;
  writeGuestCart(items);
}

function removeFromGuestCart(cartItemId) {
  writeGuestCart(readGuestCart().filter((item) => item.bookId !== cartItemId));
}

export async function getCart() {
  if (!isLoggedIn()) return getGuestCart();
  if (USE_MOCK) return mockGetCart();
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  const { data } = await apiClient.get("/cart");
  return data.data;
}

export async function addToCart(bookId, quantity) {
  if (!isLoggedIn()) return addToGuestCart(bookId, quantity);
  if (USE_MOCK) return mockAddToCart(bookId, quantity);
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  const { data } = await apiClient.post("/cart", { bookId, quantity });
  return data.data;
}

export async function updateQuantity(cartItemId, quantity) {
  if (!isLoggedIn()) return updateGuestCartQuantity(cartItemId, quantity);
  if (USE_MOCK) return mockUpdateQuantity(cartItemId, quantity);
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  const { data } = await apiClient.patch(`/cart/${cartItemId}`, { quantity });
  return data.data;
}

export async function removeFromCart(cartItemId) {
  if (!isLoggedIn()) return removeFromGuestCart(cartItemId);
  if (USE_MOCK) return mockRemoveFromCart(cartItemId);
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  await apiClient.delete(`/cart/${cartItemId}`);
}

function toDisplayItem(cartItem) {
  return {
    id: cartItem.cartItemId,
    bookId: cartItem.bookId,
    title: cartItem.title,
    price: cartItem.price,
    quantity: cartItem.quantity,
    coverUrl: cartItem.coverImageUrl ?? null,
    shippingFee: cartItem.shippingFee ?? 3000,
    option: cartItem.option,
    condition: cartItem.condition,
  };
}

export async function fetchCartItems() {
  const { items } = await getCart();
  return items.map(toDisplayItem);
}

export async function fetchCartBenefits() {
  if (USE_MOCK) return MOCK_CART_BENEFITS;
  const { data } = await apiClient.get("/cart/benefits");
  return data;
}
```

- [ ] **Step 2: 수동 확인 — 게스트 카트**

`npm run dev` 실행(mock 모드 기본값) 후 브라우저에서 `/cart` 접속 전에 devtools 콘솔에서 게스트 카트를 시딩:

```js
localStorage.setItem("guest_cart", JSON.stringify([{ bookId: 1, quantity: 2 }, { bookId: 2, quantity: 1 }]));
```

이후 `/cart` 페이지로 이동(또는 새로고침). Expected: "자바 ORM 표준 JPA 프로그래밍"(수량 2)과 "클린 코드"(수량 1)가 정상 표시됨(가격/수량 합계 포함). devtools Application 탭에서 `guest_cart` 키 값이 그대로 유지되는지 확인.

- [ ] **Step 3: 수동 확인 — 로그인 mock 카트**

콘솔에서 `localStorage.setItem("auth_tokens", JSON.stringify({accessToken:"t", tokenType:"Bearer"}))` 후 `/cart` 새로고침. Expected: `MOCK_CART_ITEMS` 3권(자바 ORM/클린 코드/해리 포터, 중고 A급 배지 포함)이 표시됨(게스트 카트가 아니라 mock 서버 카트가 보여야 함). 확인 후 `localStorage.removeItem("auth_tokens")`로 원복.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/api/cart.js
git commit -m "feat: 로그인 여부에 따른 장바구니 저장소 분기 구현 (BOO-23)"
```

---

### Task 7: `mergeCart` — 로그인 시 게스트 카트 병합

**Files:**
- Modify: `frontend/src/api/auth.js`

**Interfaces:**
- Consumes: `getCart, addToCart, updateQuantity` from `./cart.js` (Task 6)
- Produces: `mergeCart(localCartItems: [{bookId, quantity}]): Promise<void>` — Task 8(`Login.jsx`)이 로그인 성공 직후 호출한다. 실패 시 예외를 던지며, 호출부(Login.jsx)가 try/catch로 감싸는 책임을 진다(이 함수 자체는 실패를 삼키지 않는다).

- [ ] **Step 1: `api/auth.js`에 추가**

`frontend/src/api/auth.js` 상단 import에 추가:

```js
import { getCart, addToCart, updateQuantity } from "./cart.js";
```

파일 맨 아래에 추가:

```js
export async function mergeCart(localCartItems) {
  if (!localCartItems || localCartItems.length === 0) return;

  const serverCart = await getCart();
  const serverItemsByBookId = new Map(serverCart.items.map((item) => [item.bookId, item]));

  for (const localItem of localCartItems) {
    const existing = serverItemsByBookId.get(localItem.bookId);
    if (existing) {
      // TODO(BOO-23): 병합 시 동일 도서 수량 처리 정책이 명세서에 없어 우선 합산으로 구현.
      await updateQuantity(existing.cartItemId, existing.quantity + localItem.quantity);
    } else {
      await addToCart(localItem.bookId, localItem.quantity);
    }
  }
}
```

로컬 아이템을 `for...of` + `await`로 **순차** 처리한다(동시에 여러 `updateQuantity` PATCH를 날리면 수량이 서로 덮어쓸 수 있어 경쟁 상태가 생기므로 `Promise.all`을 쓰지 않는다).

- [ ] **Step 2: 수동 확인**

콘솔에서 로그인 상태를 시딩:

```js
localStorage.setItem("auth_tokens", JSON.stringify({ accessToken: "t", tokenType: "Bearer" }));
localStorage.setItem("guest_cart", JSON.stringify([{ bookId: 1, quantity: 2 }, { bookId: 5, quantity: 1 }]));
```

아직 `mergeCart`를 호출하는 UI가 없으므로(Task 8에서 연결됨), 이 태스크는 `npm run build`로 문법만 확인한다:

```bash
cd frontend && npm run build
```

Expected: 빌드 성공. 실제 병합 동작(자바 ORM 수량이 1→3으로 합산되고, bookId 5가 새 줄로 추가되는지)은 Task 9(App.jsx 라우팅 완료 후 `/login` 접속 가능해짐)에서 로그인 흐름을 통해 확인한다. 확인 후 `localStorage.removeItem("auth_tokens")`, `localStorage.removeItem("guest_cart")`로 정리.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/api/auth.js
git commit -m "feat: 로그인 시 게스트 장바구니 병합 로직 추가 (BOO-23)"
```

---

### Task 8: `Login.jsx` 페이지

**Files:**
- Create: `frontend/src/pages/Login.jsx`

**Interfaces:**
- Consumes: `login, mergeCart` from `../api/auth.js` (Task 4, 7); `getGuestCartItems, clearGuestCart` from `../api/cart.js` (Task 6); `useAuth` from `../context/AuthContext.jsx` (Task 2); `useToast` from `../components/Toast.jsx`(기존); `Button` from `../components/Button.jsx`(기존).
- Produces: `Login` 컴포넌트(default export) — `App.jsx`(Task 9)가 `/login` 라우트에 등록한다.

- [ ] **Step 1: 파일 작성**

```jsx
// frontend/src/pages/Login.jsx
import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { BookOpen } from "lucide-react";
import Button from "../components/Button.jsx";
import { useToast } from "../components/Toast.jsx";
import { useAuth } from "../context/AuthContext.jsx";
import { login, mergeCart } from "../api/auth.js";
import { getGuestCartItems, clearGuestCart } from "../api/cart.js";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const { login: authLogin } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const redirectTo = location.state?.from?.pathname ?? "/";

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      toast.error("이메일과 비밀번호를 입력해주세요.");
      return;
    }

    setIsSubmitting(true);
    try {
      const tokenResponse = await login(email, password);
      authLogin(tokenResponse);

      const guestItems = getGuestCartItems();
      try {
        await mergeCart(guestItems);
        clearGuestCart();
      } catch {
        // 백엔드 cart 모듈이 아직 없어 병합은 항상 실패할 수 있다(BOO-23 TODO).
        // 병합 실패가 로그인 자체(토큰 저장, 리다이렉트)를 막으면 안 되므로 여기서 무시한다.
      }

      navigate(redirectTo, { replace: true });
    } catch (err) {
      const message =
        err.response?.data?.error?.message ??
        err.response?.data?.message ??
        "로그인에 실패했습니다. 잠시 후 다시 시도해주세요.";
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-4xl items-stretch px-4 py-12 sm:px-6">
      <div className="flex w-full overflow-hidden rounded-2xl bg-white shadow-[0_10px_30px_rgba(27,59,54,0.10)]">
        {/* 왼쪽 브랜드 스트립 */}
        <div className="hidden w-64 shrink-0 flex-col justify-between bg-[var(--color-forest)] p-8 text-[var(--color-paper)] sm:flex">
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--color-honey)] text-[var(--color-forest)]">
              <BookOpen size={18} strokeWidth={2.25} />
            </span>
            <span className="font-display text-lg">책 먹는 사자</span>
          </div>
          <p className="text-sm leading-relaxed text-[var(--color-paper)]/70">
            로그인하고 담아둔 책을
            <br />
            그대로 이어서 결제해보세요.
          </p>
        </div>

        {/* 로그인 폼 */}
        <div className="flex flex-1 flex-col justify-center p-8 sm:p-10">
          <h1 className="font-display mb-1 text-2xl text-[var(--color-forest)]">로그인</h1>
          <p className="mb-6 text-sm text-[var(--color-ink)] opacity-70">
            책 먹는 사자와 함께 다음 책을 골라보세요.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">이메일</span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                autoComplete="email"
                className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">비밀번호</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호를 입력하세요"
                autoComplete="current-password"
                className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
              />
            </label>

            <Button type="submit" variant="primary" size="lg" fullWidth loading={isSubmitting} className="mt-2">
              로그인
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 수동 확인은 Task 9 완료 후 진행**

이 파일은 아직 어떤 라우트에도 연결되지 않아 브라우저에서 `/login`으로 접속할 수 없다(Task 9에서 `App.jsx`에 등록됨). 이 단계에서는 문법만 확인한다:

```bash
cd frontend && npm run build
```

Expected: 빌드 성공. 로그인 실패/성공/리다이렉트/게스트 카트 병합 동작의 실제 확인은 Task 9의 Step 3에서 함께 진행한다.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/pages/Login.jsx
git commit -m "feat: 로그인 페이지 구현 (BOO-23)"
```

---

### Task 9: `ProtectedRoute` + `App.jsx` 라우팅

**Files:**
- Create: `frontend/src/components/ProtectedRoute.jsx`
- Modify: `frontend/src/App.jsx`

**Interfaces:**
- Consumes: `useAuth`, `AuthProvider` from `../context/AuthContext.jsx` (Task 2); `Login` from `./pages/Login.jsx` (Task 8).
- Produces: `ProtectedRoute({children})` 컴포넌트 — 비로그인 시 `/login`으로 리다이렉트하며 `state: {from: location}`을 전달한다.

- [ ] **Step 1: `ProtectedRoute.jsx` 작성**

```jsx
// frontend/src/components/ProtectedRoute.jsx
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";

export default function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}
```

- [ ] **Step 2: `App.jsx` 전체 교체**

```jsx
// frontend/src/App.jsx
import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";
import Header from "./components/Header.jsx";
import { ToastProvider } from "./components/Toast.jsx";
import { AuthProvider } from "./context/AuthContext.jsx";
import ProtectedRoute from "./components/ProtectedRoute.jsx";
import Login from "./pages/Login.jsx";
import Cart from "./pages/Cart.jsx";
import Checkout from "./pages/Checkout.jsx";
import MyPage from "./pages/MyPage.jsx";

function Layout() {
  return (
    <div className="min-h-screen bg-[var(--color-paper)]">
      <Header cartCount={3} wishlistCount={2} />
      <Outlet />
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route element={<Layout />}>
              {/* ProductList는 오현님 작업 예정 - 지금은 Header만 노출 */}
              <Route path="/" element={null} />
              <Route path="/login" element={<Login />} />
              <Route path="/cart" element={<Cart />} />
              <Route
                path="/checkout"
                element={
                  <ProtectedRoute>
                    <Checkout />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mypage"
                element={
                  <ProtectedRoute>
                    <MyPage />
                  </ProtectedRoute>
                }
              />
            </Route>
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
```

- [ ] **Step 3: 수동 확인 — 라우트 가드**

`npm run dev` 실행 후:
1. devtools 콘솔에서 `localStorage.removeItem("auth_tokens")`로 비로그인 상태 확실히 하고 `/checkout` 직접 접속. Expected: `/login`으로 리다이렉트됨.
2. `/mypage` 직접 접속. Expected: `/login`으로 리다이렉트됨.
3. `/cart` 직접 접속. Expected: 리다이렉트 없이 그대로 열림(게스트도 접근 가능).

- [ ] **Step 4: 수동 확인 — 로그인 실패**

1. `/login` 접속, 이메일 `test@test.com`, 비밀번호 `wrong` 입력 후 로그인 클릭. Expected: 우측 하단에 Toast로 "이메일 또는 비밀번호가 올바르지 않습니다." 표시(alert 없음).

- [ ] **Step 5: 수동 확인 — 로그인 성공 + 리다이렉트**

1. devtools 콘솔에서 `localStorage.removeItem("auth_tokens")` 후 `/checkout` 접속 → `/login`으로 리다이렉트됨을 확인.
2. 이메일 `test@test.com`, 비밀번호 `1234` 입력 후 로그인. Expected: `/checkout`으로 되돌아감(리다이렉트 성공). devtools Application 탭에서 `auth_tokens` 키에 `mock-access-token`이 저장돼 있는지 확인.

- [ ] **Step 6: 수동 확인 — 게스트 카트 병합**

1. 로그아웃 상태로 만들고(`localStorage.removeItem("auth_tokens")`) `localStorage.setItem("guest_cart", JSON.stringify([{bookId:1,quantity:2},{bookId:5,quantity:1}]))` 실행.
2. `/login`에서 정상 로그인(비밀번호 `wrong` 아닌 값).
3. 로그인 후 `/cart` 접속. Expected: bookId 1(자바 ORM)의 수량이 기존 mock 1 + 게스트 2 = **3**으로 합산되어 표시되고, bookId 5(`도서 #5`, 카탈로그에 없어 제목이 이렇게 표시됨)가 새 줄로 추가되어 있음.
4. devtools Application 탭에서 `guest_cart` 키가 비어있거나(`[]`) 삭제되어 있는지 확인(병합 성공 시 비워짐).

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/components/ProtectedRoute.jsx frontend/src/App.jsx
git commit -m "feat: 체크아웃/마이페이지 인증 가드 추가 (BOO-23)"
```

---

### Task 10: `Header.jsx` — 로그인/로그아웃 버튼

**Files:**
- Modify: `frontend/src/components/Header.jsx`

**Interfaces:**
- Consumes: `useAuth` from `../context/AuthContext.jsx` (Task 2)

- [ ] **Step 1: import 구문 수정**

`frontend/src/components/Header.jsx` 최상단을 다음과 같이 바꾼다:

```jsx
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Search, Heart, ShoppingBag, User, BookOpen } from "lucide-react";
import { useAuth } from "../context/AuthContext.jsx";
```

- [ ] **Step 2: 컴포넌트 본문에 인증 상태/핸들러 추가**

`export default function Header({ cartCount = 0, wishlistCount = 0 }) {` 바로 다음 줄(`const [query, setQuery] = useState("");` 다음)에 추가:

```jsx
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/");
  };
```

- [ ] **Step 3: 마이페이지 아이콘 앞에 로그인/로그아웃 버튼 추가**

기존:

```jsx
            <Link
              to="/mypage"
              aria-label="마이페이지"
              className="flex h-10 w-10 items-center justify-center rounded-full text-[var(--color-forest)] transition-colors hover:bg-[var(--color-forest)]/10"
            >
              <User size={20} />
            </Link>
```

바로 앞에 삽입:

```jsx
            {isAuthenticated ? (
              <button
                type="button"
                onClick={handleLogout}
                className="hidden text-sm font-medium text-[var(--color-ink)]/70 transition-colors hover:text-[var(--color-coral)] sm:block"
              >
                로그아웃
              </button>
            ) : (
              <Link
                to="/login"
                className="hidden text-sm font-medium text-[var(--color-ink)]/70 transition-colors hover:text-[var(--color-coral)] sm:block"
              >
                로그인
              </Link>
            )}
```

- [ ] **Step 4: 수동 확인**

1. 비로그인 상태(`localStorage.removeItem("auth_tokens")`)에서 아무 페이지나 열기. Expected: 헤더 우측에 "로그인" 텍스트 링크가 보임. 클릭 시 `/login`으로 이동.
2. `/login`에서 정상 로그인. Expected: 헤더의 "로그인"이 "로그아웃"으로 바뀜.
3. "로그아웃" 클릭. Expected: 홈(`/`)으로 이동하고 헤더가 다시 "로그인"으로 바뀜. devtools Application 탭에서 `auth_tokens` 키가 삭제됐는지 확인.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/Header.jsx
git commit -m "feat: 헤더에 로그인/로그아웃 버튼 추가 (BOO-23)"
```

---

### Task 11: `Cart.jsx` — 수량 변경/삭제를 실제 저장소에 반영

**Files:**
- Modify: `frontend/src/pages/Cart.jsx`

**Interfaces:**
- Consumes: `updateQuantity, removeFromCart` from `../api/cart.js` (Task 6) — 추가로 import.

- [ ] **Step 1: import 구문 수정**

기존:

```js
import { fetchCartItems, fetchCartBenefits } from "../api/cart.js";
```

변경:

```js
import { fetchCartItems, fetchCartBenefits, updateQuantity, removeFromCart } from "../api/cart.js";
```

- [ ] **Step 2: `changeQuantity` 교체**

기존:

```js
  const changeQuantity = (id, delta) => {
    setItems((prev) =>
      prev.map((item) =>
        item.id === id ? { ...item, quantity: Math.max(1, item.quantity + delta) } : item
      )
    );
  };
```

변경:

```js
  const changeQuantity = (id, delta) => {
    const target = items.find((item) => item.id === id);
    if (!target) return;
    const newQuantity = Math.max(1, target.quantity + delta);
    setItems((prev) => prev.map((item) => (item.id === id ? { ...item, quantity: newQuantity } : item)));
    // 낙관적으로 화면은 먼저 갱신하고, 저장소 반영 실패는 조용히 무시한다
    // (백엔드 cart 모듈 미구현으로 로그인 상태의 실API 호출은 항상 실패할 수 있음 — BOO-23 TODO).
    updateQuantity(id, newQuantity).catch(() => {});
  };
```

- [ ] **Step 3: `removeItem` 교체**

기존:

```js
  const removeItem = (id) => {
    setItems((prev) => prev.filter((item) => item.id !== id));
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  };
```

변경:

```js
  const removeItem = (id) => {
    setItems((prev) => prev.filter((item) => item.id !== id));
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
    removeFromCart(id).catch(() => {});
  };
```

- [ ] **Step 4: `removeSelected` 교체**

기존:

```js
  const removeSelected = () => {
    setItems((prev) => prev.filter((item) => !selectedIds.has(item.id)));
    setSelectedIds(new Set());
  };
```

변경:

```js
  const removeSelected = () => {
    const idsToRemove = [...selectedIds];
    setItems((prev) => prev.filter((item) => !selectedIds.has(item.id)));
    setSelectedIds(new Set());
    idsToRemove.forEach((id) => removeFromCart(id).catch(() => {}));
  };
```

- [ ] **Step 5: `CartItemRow`의 옵션 표시 수정 (`option`이 없는 실API/게스트 아이템 대응)**

기존:

```jsx
              <span className="text-sm text-[var(--color-ink)] opacity-70">
                {item.option} · 배송비 {item.shippingFee.toLocaleString()}원
              </span>
```

변경:

```jsx
              <span className="text-sm text-[var(--color-ink)] opacity-70">
                {item.option ? `${item.option} · ` : ""}배송비 {item.shippingFee.toLocaleString()}원
              </span>
```

- [ ] **Step 6: 수동 확인**

1. 게스트 카트 시딩 후(`localStorage.setItem("guest_cart", JSON.stringify([{bookId:1,quantity:1}]))`) `/cart` 접속. 수량 `+` 버튼 클릭 → 화면에 수량 2로 반영됨을 확인.
2. 페이지를 새로고침. Expected: 수량이 2로 유지됨(= `localStorage["guest_cart"]`에 실제로 반영됐다는 뜻 — 이전에는 새로고침하면 1로 원복됐음).
3. 삭제(휴지통) 버튼 클릭 → 목록에서 사라짐. 새로고침 후에도 다시 나타나지 않는지 확인.
4. 로그인 상태(mock)에서 동일하게 수량 변경/삭제가 화면에 반영되는지 확인(mock 서버 카트는 새로고침 시 초기값으로 리셋되는 것이 정상 — Task 5에서 의도한 동작).
5. `MOCK_CART_ITEMS`의 중고 아이템("해리 포터", condition `A`) 행에서 "중고 · A급" 배지와 배송비 텍스트가 여전히 정상 표시되는지 확인(Step 5 변경으로 깨지지 않았는지).

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/pages/Cart.jsx
git commit -m "feat: 장바구니 수량 변경/삭제를 저장소에 반영 (BOO-23)"
```

---

### Task 12: 전체 흐름 End-to-End 수동 QA

**Files:** 없음(수동 검증 전용 — 문제 발견 시 해당 태스크의 파일로 돌아가 수정).

- [ ] **Step 1: 비회원 장바구니 유지**

1. 로그아웃 상태 확인(`auth_tokens` 없음).
2. 콘솔에서 `localStorage.setItem("guest_cart", JSON.stringify([{bookId:2,quantity:1}]))` 후 `/cart` 접속 → "클린 코드" 표시 확인.
3. `/checkout` 접속 시도 → `/login`으로 리다이렉트되는지 확인(장바구니 내용은 그대로 `guest_cart`에 남아있어야 함).

- [ ] **Step 2: 로그인 → 병합 → 결제 페이지 복귀**

1. `/login`에서 정상 로그인(비밀번호 `wrong` 아님).
2. `/checkout`으로 자동 복귀하는지 확인(Task 9 Step 5에서 검증한 리다이렉트가 실제 체크아웃 진입 시나리오에서도 동작하는지 재확인).
3. devtools에서 `guest_cart`가 비워졌는지, `auth_tokens`에 mock 토큰이 저장됐는지 확인.

- [ ] **Step 3: 마이페이지 접근 및 로그아웃**

1. `/mypage` 접속 → 정상 진입(리다이렉트 없음) 확인.
2. 헤더의 "로그아웃" 클릭 → 홈으로 이동, `auth_tokens` 삭제 확인.
3. 다시 `/mypage` 접속 → `/login`으로 리다이렉트되는지 확인.

- [ ] **Step 4: 콘솔 에러 확인**

1~3 과정 전체를 진행하는 동안 브라우저 devtools 콘솔에 처리되지 않은 에러(빨간색, 특히 `Uncaught` 또는 React 경고 중 이 기능과 관련된 것)가 없는지 확인한다. `mergeCart`/`updateQuantity`/`removeFromCart`의 실API 분기가 실패하며 발생하는 네트워크 에러(404 등, 백엔드 cart 모듈 부재로 인한 예상된 실패)는 정상이며 무시한다.

문제가 발견되면 해당 동작을 구현한 태스크(Task 6~11)로 돌아가 수정하고, 그 태스크의 커밋 이후에 별도 커밋으로 fix를 남긴다(`fix: <설명> (BOO-23)`).
