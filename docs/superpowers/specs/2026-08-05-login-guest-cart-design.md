# 로그인 화면 + 비회원 장바구니 허용 흐름 설계 (BOO-23)

- Date: 2026-08-05
- Scope: `frontend/src/pages/Login.jsx`, `frontend/src/api/auth.js`, `frontend/src/mocks/auth.js`, `frontend/src/api/cart.js`, `frontend/src/mocks/cart.js`, `frontend/src/context/AuthContext.jsx`, `frontend/src/components/ProtectedRoute.jsx`, `frontend/src/api/client.js`, `frontend/src/App.jsx`, `frontend/src/components/Header.jsx`
- Status: Approved
- Branch: `feature/BOO-23-login-ui`

## 배경

교보문고/YES24 방식: 비로그인 상태에서도 장바구니 담기가 가능하고, 결제(Checkout)·마이페이지 진입 시점에만 로그인을 요구한다. 현재 프론트엔드에는 로그인 화면, 전역 인증 상태, 토큰 저장, 장바구니 mutation API(추가/수정/삭제)가 전혀 없다.

## 조사 결과 (기존 코드 확인)

- `App.jsx`: react-router-dom v7, `Layout`(Header+Outlet) 아래 flat routes(`/`, `/cart`, `/checkout`, `/mypage`). 라우트 가드 없음.
- `api/client.js`: 순수 axios 인스턴스, 인터셉터 없음 → Authorization 헤더 자동 첨부 로직 없음.
- `api/cart.js`: `fetchCartItems`/`fetchCartBenefits`(조회 전용 GET)만 존재. `addToCart`/`updateQuantity`/`removeFromCart`는 없음. `Cart.jsx`는 수량 변경/삭제를 로컬 React state로만 처리(서버 반영 없음). `BookCard`의 "담기" 버튼은 `onAddToCart` prop 콜백만 있고 api 연동 없음.
- 인증 상태/토큰 저장소: 프로젝트 전체에 없음. Context/전역 상태는 `ToastProvider`가 유일.
- 백엔드: `/api/auth/login`, `/api/auth/refresh`, `/api/members/me`는 이미 구현됨(`docs/superpowers/specs/2026-08-04-auth-member-api-design.md`). `TokenResponse(accessToken, refreshToken, tokenType, expiresIn)`, `LoginRequest(email, password)`. `/api/members/me`는 JWT Bearer 인증 필요.
- 백엔드에 **cart 모듈이 아직 없다**(`backend/modules`에 book/common/delivery/member/order/usedbook만 존재). 다만 API 명세서 섹션 0(장바구니)에 계약은 이미 확정돼 있음(사용자 확인):
  - `GET /api/cart` → 장바구니 조회
  - `POST /api/cart {bookId, quantity}` → 담기
  - `PATCH /api/cart/{cartItemId} {quantity}` → 수량 변경
  - `DELETE /api/cart/{cartItemId}` → 삭제
  - `POST /api/cart/checkout` → 주문 전환(이번 스코프 밖)
  - 공통: JWT Bearer 인증, camelCase 필드, 응답 포맷 `{success, data, error}`
  - `GET /api/cart` 응답: `{items: [{cartItemId, bookId, title, coverImageUrl, price, quantity}], totalPrice}` (cart_items 테이블은 book 상세를 갖지 않아 서버가 book과 JOIN해서 내려줌)
  - PK 컨벤션상 `cartItemId`
- `GET /api/books/{bookId}` 존재, `BookDetailResponse{id, title, author, publisher, isbn, category, price, stockQuantity, coverImageUrl, description, saleStatus, publishedDate, createdAt, updatedAt}` 반환 → 비회원 장바구니 아이템 화면 표시용 책 정보 조회에 사용.
- **응답 envelope 미언래핑 이슈**: 백엔드 `ApiResponse<T>`는 `{success, message, data, error}`로 감싸져 오는데, 기존 `mypage.js`/`cart.js`/`checkout.js`는 `const {data} = await apiClient.get(...); return data;` 형태라 envelope 자체를 그대로 반환 중(`MyPage.jsx`가 `profile.name`으로 바로 접근하는 것으로 확인 — 실API 연결 시 깨지는 상태). 기존 파일은 스코프 밖이라 손대지 않는다. 이번에 새로 만드는 `auth.js`/`cart.js`의 실API 분기는 `return data.data`로 정상 언래핑하고, 기존 패턴과 다르다는 점을 파일 상단 주석으로 명시한다.

## 아키텍처

```
AuthContext (React state, localStorage["auth_tokens"] 미러링)
   ├─ Header.jsx: 로그인 여부 → 로그인/로그아웃 버튼
   ├─ ProtectedRoute: 로그인 여부 → /login 리다이렉트
   └─ Login.jsx: 로그인 성공 시 context.login() 호출

api/client.js
   └─ 요청 인터셉터: localStorage["auth_tokens"] 직접 읽어 Authorization 헤더 첨부
      (Context와 별개 경로 — React 트리 밖에서도 항상 최신 토큰 사용)

api/cart.js
   └─ isLoggedIn() = localStorage["auth_tokens"] 존재 여부
      ├─ 비로그인 → localStorage["guest_cart"] CRUD
      └─ 로그인 → 실API(POST/PATCH/DELETE/GET /api/cart) 또는 mock
   컴포넌트(Cart.jsx, BookCard)는 getCart/addToCart/updateQuantity/removeFromCart만 호출,
   내부 분기는 의식하지 않음.

api/auth.js
   └─ login() → AuthContext.login(tokenResponse) 저장 후
      mergeCart(guestCartItems) 호출 (getCart/addToCart/updateQuantity 재사용,
      이 시점엔 이미 토큰이 저장돼 있으므로 자동으로 서버 분기를 탐)
```

## 컴포넌트별 설계

### 1. `src/context/AuthContext.jsx` (신규)
- localStorage key `auth_tokens`에 `TokenResponse` 그대로 JSON 저장.
- `AuthProvider`: 마운트 시 hydrate, `isAuthenticated`(accessToken 존재 여부로 파생), `login(tokenResponse)`(state+storage 저장), `logout()`(state+storage 제거) 제공.
- `useAuth()` 훅으로 소비.
- `App.jsx`: `ToastProvider` 내부에 `AuthProvider` 추가.

### 2. `src/api/client.js` (수정)
- 요청 인터셉터 추가: `localStorage.getItem("auth_tokens")` 파싱해 `accessToken` 있으면 `Authorization: {tokenType} {accessToken}` 헤더 첨부.

### 3. `src/api/auth.js` (신규) + `src/mocks/auth.js` (신규)
- `login(email, password)`: `USE_MOCK` 분기. 실API `POST /api/auth/login {email, password}` → envelope 언래핑(`data.data`) 후 `TokenResponse` 반환.
- `mergeCart(localCartItems)`:
  - `localCartItems.length === 0`이면 즉시 return.
  - `getCart()`(서버 분기, 이미 로그인 상태)로 서버 장바구니 조회.
  - 로컬 아이템을 **순차 for-of + await**로 순회(동시 PATCH로 인한 수량 경쟁 방지):
    - 서버에 동일 `bookId` 있으면 `updateQuantity(cartItemId, 서버수량 + 로컬수량)` — `// TODO(BOO-23): 병합 시 동일 도서 수량 처리 정책이 명세서에 없어 우선 합산으로 구현`
    - 없으면 `addToCart(bookId, quantity)`
- `mocks/auth.js`: 고정 mock `TokenResponse` 1개 + 실패 케이스(`password === "wrong"` → `{response: {data: {error: {message: "..."}}}}` 형태 reject, 실API 에러 shape와 동일하게 맞춰 Login.jsx 에러 처리 분기를 mock/실API 공통으로 사용 가능하게 함).

### 4. `src/api/cart.js` (재설계) + `src/mocks/cart.js` (확장)
- `isLoggedIn()`: `localStorage.getItem("auth_tokens")` 존재 여부.
- Guest 저장소: `localStorage["guest_cart"]` = `[{bookId, quantity}]`.
- 공개 함수(컴포넌트가 호출하는 유일한 인터페이스): `getCart()`, `addToCart(bookId, quantity)`, `updateQuantity(cartItemId, quantity)`, `removeFromCart(cartItemId)`. 내부에서 `isLoggedIn()` 분기 후 guest/서버(mock 포함) 처리로 위임.
- 실API 분기(로그인 상태): 확정된 스펙대로 `GET/POST/PATCH/DELETE /api/cart`. 각 실API 분기 상단에 `// TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작` 명시.
- Guest 모드 `getCart()`: `guest_cart`의 각 `{bookId, quantity}`에 대해 `GET /api/books/{bookId}`(mock 모드에선 `mocks/cart.js`에 추가하는 소규모 mock 책 카탈로그)를 조회해 `{cartItemId, bookId, title, coverImageUrl, price, quantity}`로 합성. **`cartItemId`는 서버 PK가 없으므로 `bookId`를 그대로 사용 — "게스트 카트는 한 책당 한 줄만 존재한다"는 전제 하에만 안전하므로 코드 주석으로 명시.**
- Guest 아이템 `shippingFee`: book API에 배송비 필드가 없어 mock 관행대로 고정값 `3000` 사용(로그인 mock 카트도 동일 기준).
- 기존 `fetchCartItems`/`fetchCartBenefits`는 `Cart.jsx`가 그대로 쓰므로 유지하되, `fetchCartItems`는 내부적으로 `getCart()`를 호출해 `items` 배열만 반환하도록 정리.
- **`Cart.jsx` 최소 수정 필요**: 현재 `changeQuantity`/`removeItem`이 로컬 React state만 바꾸고 서버/localStorage에 전혀 반영하지 않는다(기존 코드의 gap). 이 상태로는 guest 카트가 새로고침 시 원상복구되어 "비회원도 장바구니 유지" 요구사항이 깨진다. `changeQuantity`/`removeItem` 안에서 기존 낙관적 local state 업데이트에 더해 `updateQuantity(item.id, newQuantity)` / `removeFromCart(item.id)`를 호출하도록 최소 연결한다(그 외 레이아웃/스타일은 손대지 않음).

### 5. `src/components/ProtectedRoute.jsx` (신규)
- `useAuth()` 확인 → 비로그인 시 `<Navigate to="/login" state={{ from: location }} replace />`.
- `App.jsx`: `/checkout`, `/mypage`만 감싸기. `/cart`, `/login`은 공개 유지.

### 6. `src/pages/Login.jsx` (신규)
- Cart/Checkout과 동일 카드형 레이아웃(왼쪽 forest 컬러 스트립 브랜드 요소 재사용).
- 이메일/비밀번호 입력 → 제출:
  1. `login(email, password)` 호출
  2. 성공: `authLogin(tokenResponse)`(context) → `guest_cart` 읽기 → `mergeCart(guestItems)`를 **try/catch로 감싸 실패해도 무시**(현재 백엔드 cart 모듈이 없어 병합은 항상 실패할 수 있음 — 병합 실패가 로그인 자체를 막으면 안 됨) → 성공 시에만 `guest_cart` 비움 → `location.state?.from?.pathname ?? "/"`로 이동
  3. 실패(1번 단계): `toast.error(...)`로 표시. `alert()` 미사용.

### 7. `Header.jsx` (수정)
- `useAuth()`로 로그인 여부 확인 → 마이페이지 아이콘 옆에 로그인/로그아웃 텍스트 버튼. 로그아웃 클릭 시 `logout()` 후 홈으로 이동.

## 에러 처리

- 로그인 실패: mock/실API 공통 에러 shape(`err.response?.data?.error?.message`)로 메시지 추출, Toast로 표시.
- 장바구니 mutation 실패(현재 백엔드 cart 모듈 부재로 로그인 상태에서 실API 호출 시 필연적으로 실패): `mergeCart` 내부는 개별 아이템 단위가 아니라 전체를 try/catch로 감싸 로그인 흐름을 막지 않음. (개별 아이템 단위 부분 실패 처리는 이번 스코프 밖 — TODO로 남김)

## 비범위 (Out of scope)

- 백엔드 cart 모듈 실제 구현(별도 티켓).
- 회원가입 화면(별도 티켓 가능성, 이번엔 로그인만).
- refresh token을 이용한 자동 갱신/만료 처리.
- 기존 `mypage.js`/`checkout.js`의 envelope 언래핑 버그 수정.
- `POST /api/cart/checkout`(장바구니→주문 전환) 연동.
