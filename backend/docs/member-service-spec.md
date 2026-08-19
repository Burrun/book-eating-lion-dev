# Member Service API 명세 & 프론트엔드 공통 구조

- 대상 모듈: `backend/modules/member/` (컨트롤러는 `backend/apps/member-api/`에 마운트)
- 대상 프론트엔드: `frontend/src/`
- 기준 브랜치: `feature/member-jwt`

---

## 섹션 1. 공통 규격

### 1.1 인증 방식

- **AWS Cognito User Pool** 기반 JWT. Spring Security `oauth2ResourceServer().jwt()`로 토큰을 검증한다 (`backend/apps/member-api/.../config/SecurityConfig.java`).
- 요청 헤더: `Authorization: Bearer {accessToken}`
- 인증된 요청은 `SecurityUtils`(`backend/modules/common/.../security/SecurityUtils.java`)로 JWT 클레임을 읽는다.
  - `currentMemberSub()` → JWT `sub` (Cognito 사용자 식별자, `Member.id`(=`member_id` PK)와 매칭)
  - `currentMemberId()` → 커스텀 클레임 `member_id` (Cognito PreTokenGeneration 단계에서 member-service가 주입). 다른 서비스가 member-service를 동기 호출하지 않고 소유권을 검증할 때 쓴다.
  - `currentNickname()` → 커스텀 클레임 `nickname`

**경로별 인증 요구사항** (`SecurityConfig`):

| 경로 패턴 | 인증 |
|---|---|
| `/actuator/**` | 불필요 |
| `/api/auth/**` | 불필요 (로그인/가입/토큰갱신은 인증 전 단계) |
| `/api/members/me/**` | 필요 (addresses 포함) |
| `/api/cards/**` | 필요 |
| `/internal/**` | 불필요 — JWT 대신 Ingress가 외부 노출 차단, NetworkPolicy가 클러스터 내부 출처만 허용 |
| 그 외 | 불필요 (`anyRequest().permitAll()`) |

### 1.2 공통 응답 포맷

`backend/modules/common/.../dto/ApiResponse.java` 기준:

```java
public class ApiResponse<T> {
    boolean success;
    String message;      // 성공 시 기본값 "SUCCESS", 실패 시 에러 메시지
    T data;               // 실패 시 null
    ErrorDetail error;    // 성공 시 null. { String code, String message }
}
```

| 생성 메서드 | success | message | data | error |
|---|---|---|---|---|
| `ApiResponse.success(data)` | true | `"SUCCESS"` | data | null |
| `ApiResponse.success(message, data)` | true | message | data | null |
| `ApiResponse.error(message)` | false | message | null | null |
| `ApiResponse.error(code, message)` | false | message | null | `{code, message}` |

> **예외**: `InternalCardController`(`/internal/cards/**`)는 `ApiResponse`로 감싸지 않고 `CardOperationResult`를 그대로 반환한다. 서비스 간 내부 호출 전용 계약(order-service의 `CardClient`)이라 외부 응답 규격과 별개다.

---

## 섹션 2. API 명세 (백엔드)

### 2.1 AuthController — `/api/auth` (인증 불필요)

| Method + URL | 인증 | 요청 (DTO) | 응답 (DTO) | 에러 |
|---|---|---|---|---|
| `POST /api/auth/signup` | 불필요 | `SignupRequest`: `email`(String, `@NotBlank @Email`), `password`(String, `@NotBlank @Size(min=8)`), `name`(String, `@NotBlank`) | `SignupResponse`: `memberId`(Long), `email`(String), `name`(String) | 400 `INVALID_REQUEST`(검증 실패) · 409 `DUPLICATE_EMAIL`(로컬 DB 또는 Cognito에 동일 이메일 존재) · 400 `INVALID_PASSWORD`(Cognito 비밀번호 정책 위반, 메시지에 대/소문자·숫자·특수문자·길이 안내 포함) · 400 `COGNITO_SIGNUP_FAILED`(그 외 Cognito 오류) |
| `POST /api/auth/login` | 불필요 | `LoginRequest`: `email`(String, `@NotBlank @Email`), `password`(String, `@NotBlank`) | `TokenResponse`: `accessToken`, `refreshToken`, `tokenType`, `expiresIn`(long) | 400 `INVALID_REQUEST` · 401 `INVALID_CREDENTIALS`(이메일/비밀번호 불일치) · 400 `COGNITO_LOGIN_FAILED` |
| `POST /api/auth/refresh` | 불필요 | `RefreshRequest`: `refreshToken`(String, `@NotBlank`) | `TokenResponse` (refreshToken이 응답에 없으면 요청값을 그대로 반환) | 400 `INVALID_REQUEST` · 401 `INVALID_REFRESH_TOKEN` · 400 `COGNITO_REFRESH_FAILED` |

에러 처리는 `MemberExceptionHandler`가 담당하며 `resolveStatus()`가 코드→HTTP 상태를 매핑한다: `INVALID_CREDENTIALS`/`INVALID_REFRESH_TOKEN` → 401, `DUPLICATE_EMAIL` → 409, 그 외 Cognito 계열 코드(`INVALID_PASSWORD`, `COGNITO_SIGNUP_FAILED`, `COGNITO_LOGIN_FAILED`, `COGNITO_REFRESH_FAILED`, `COGNITO_CONFIG_ERROR`)는 전부 → **400**.

### 2.2 MemberController — `/api/members` (인증 필요)

| Method + URL | 인증 | 요청 (DTO) | 응답 (DTO) | 에러 |
|---|---|---|---|---|
| `GET /api/members/me` | 필요 | 없음 (JWT `sub`로 조회) | `MemberResponse`: `id`(Long), `email`, `name`, `phoneNumber`, `gender`(`MALE`\|`FEMALE`), `birthDate`(LocalDate), `role`(`USER`\|`ADMIN`) | 404 `MEMBER_NOT_FOUND`(JWT sub에 해당하는 로컬 회원 레코드 없음) |
| `PATCH /api/members/me` | 필요 | `MemberUpdateRequest`: `name`, `phoneNumber`, `gender`, `birthDate` — 전부 제약조건 없음(부분 수정, null 허용) | `MemberResponse` | 404 `MEMBER_NOT_FOUND` |

### 2.3 AddressController — `/api/members/me/addresses` (인증 필요)

| Method + URL | 인증 | 요청 (DTO) | 응답 (DTO) | 에러 |
|---|---|---|---|---|
| `GET /api/members/me/addresses` | 필요 | 없음 | `List<AddressResponse>`: `id`, `recipientName`, `phoneNumber`, `zipcode`, `address`, `detailAddress`, `isDefault`(boolean), `createdAt`, `updatedAt`(LocalDateTime) — 기본배송지 우선, 생성일 오름차순 정렬 | — |
| `POST /api/members/me/addresses` (201) | 필요 | `AddressCreateRequest`: `recipientName`(`@NotBlank`), `phoneNumber`(`@NotBlank`), `zipcode`(`@NotBlank`), `address`(`@NotBlank`), `detailAddress`(제약없음), `isDefault`(boolean) | `AddressResponse` | 400 `INVALID_REQUEST`(검증 실패) |

`isDefault=true`로 생성하면 기존 기본배송지가 자동으로 해제된다(`AddressService.createAddress`). 에러코드 enum(`AddressErrorCode`)에는 `ADDRESS_NOT_FOUND`(404)도 정의돼 있으나, 현재 컨트롤러에는 이를 던지는 엔드포인트(수정/삭제)가 없다 — 향후 확장 대비 예약 코드.

### 2.4 CardController — `/api/cards` (인증 필요)

| Method + URL | 인증 | 요청 (DTO) | 응답 (DTO) | 에러 |
|---|---|---|---|---|
| `POST /api/cards` (201) | 필요 | `IssueCardRequest`: `monthlyLimit`(Long, `@Min(1000)`, nullable — body 자체도 생략 가능, 생략 시 기본 한도 1,000,000) | `CardResponse`: `cardId`, `maskedCardNumber`, `cardStatus`(`ACTIVE`\|`SUSPENDED`\|`CLOSED`), `monthlyLimit`(long), `currentUsage`(long), `issuedDate`, `expiryDate`(발급일+3년) | 400 `INVALID_REQUEST`(monthlyLimit < 1000) |
| `GET /api/cards/me` | 필요 | 없음 | `List<CardResponse>` | — |
| `PATCH /api/cards/{cardId}/status` | 필요 | `ChangeCardStatusRequest`: `cardStatus`(`@NotNull`) | `CardResponse` | 400 `INVALID_REQUEST` · 404 `CARD_NOT_FOUND` · 403 `UNAUTHORIZED_CARD_ACCESS`(본인 카드가 아님) · 400 `INVALID_CARD_STATUS_CHANGE`(이미 `CLOSED` 상태인 카드는 변경 불가) |

### 2.5 InternalCardController — `/internal/cards` (클러스터 내부 전용, JWT 불필요)

order-service의 `CardClient` 전용 계약. `/internal/**`은 애플리케이션 레벨 인증이 없고 Ingress/NetworkPolicy로만 보호된다. **응답이 `ApiResponse`로 감싸이지 않는다.**

| Method + URL | 인증 | 요청 (DTO) | 응답 (DTO) | 비고 |
|---|---|---|---|---|
| `POST /internal/cards/{cardId}/deduct` | 불필요(내부전용) | `CardOperationRequest`: `amount`(long, `@Min(1)`) | `CardOperationResult`: `approved`(boolean), `message`(String, nullable) | 카드 없음/한도초과/비활성 상태는 예외가 아니라 `approved=false` 정상 응답. order 서비스가 재고처럼 트랜잭션을 롤백하지 않고 200으로 거절을 표현하기 위한 설계 |
| `POST /internal/cards/{cardId}/restore` | 불필요(내부전용) | `CardOperationRequest`: `amount`(long, `@Min(1)`) | `CardOperationResult` | 취소/환불 시 사용량 복구. 0 미만으로 내려가지 않도록 방어 |

### 2.6 에러 코드 요약

| 코드 | HTTP | 발생 컨트롤러 | 상황 |
|---|---|---|---|
| `INVALID_REQUEST` | 400 | 전체 | `@Valid` 검증 실패 (필드별 첫 에러만 메시지에 포함) |
| `DUPLICATE_EMAIL` | 409 | Auth | 회원가입 시 이메일 중복 (로컬 DB 유니크 제약 위반 포함) |
| `INVALID_PASSWORD` | 400 | Auth | Cognito 비밀번호 정책 위반 |
| `COGNITO_SIGNUP_FAILED` / `COGNITO_LOGIN_FAILED` / `COGNITO_REFRESH_FAILED` / `COGNITO_CONFIG_ERROR` | 400 | Auth | Cognito 호출 실패(일시적 오류 포함) |
| `INVALID_CREDENTIALS` | 401 | Auth | 로그인 시 이메일/비밀번호 불일치 |
| `INVALID_REFRESH_TOKEN` | 401 | Auth | 리프레시 토큰 만료/무효 |
| `MEMBER_NOT_FOUND` | 404 | Member | JWT sub에 대응하는 회원 레코드 없음 |
| `ADDRESS_NOT_FOUND` | 404 | Address | 정의만 되어 있고 현재 사용되는 엔드포인트 없음 |
| `CARD_NOT_FOUND` | 404 | Card | 존재하지 않는 cardId |
| `UNAUTHORIZED_CARD_ACCESS` | 403 | Card | 본인 소유가 아닌 카드 접근 |
| `INVALID_CARD_STATUS_CHANGE` | 400 | Card | `CLOSED` 카드의 상태 변경 시도 |

---

## 섹션 3. 프론트엔드 공통 구조

### 3.1 인증 흐름

- **`context/AuthContext.jsx`**: `tokens` state를 `authStorage.readTokens()`로 초기화. `isAuthenticated = Boolean(tokens?.accessToken)`. `login(tokenResponse)`는 저장소에 쓰고 state를 갱신, `logout()`은 저장소를 비우고 state를 `null`로. `useAuth()` 훅으로 어디서든 접근.
- **`components/ProtectedRoute.jsx`**: `useAuth()`로 `isAuthenticated` 확인. 비로그인 시 `<Navigate to="/login" state={{ from: location }} replace />`로 리다이렉트(로그인 후 원래 경로로 돌아오도록 `from` 전달). 로그인 상태면 `children`을 그대로 렌더링.
- **토큰 저장 — `api/authStorage.js`**: `localStorage` 키 `auth_tokens`에 `TokenResponse` 전체를 JSON으로 저장. `readTokens()` / `writeTokens()` / `clearTokens()` / `isLoggedIn()` 제공.
- **자동 첨부 — `api/client.ts`**: axios 요청 인터셉터가 매 요청마다 `readTokens()`로 토큰을 읽어 `Authorization: {tokenType ?? 'Bearer'} {accessToken}` 헤더를 자동으로 붙인다. 별도로 로그인 연동 이전부터 쓰던 레거시 헬퍼 `setMemberId()`(`X-Member-Id` 헤더, 리뷰/찜/마이페이지용)와 `setAccessToken()`도 남아있으며, 코드 주석상 JWT 전환이 끝나면 `X-Member-Id` 방식은 제거될 예정.

### 3.2 API 레이어 구조

`src/api/` 파일별 담당 도메인:

| 파일 | 담당 도메인 | 비고 |
|---|---|---|
| `client.ts` | axios 인스턴스, 인터셉터, `unwrap()` 헬퍼 | 아래 참고 |
| `types.ts` | 백엔드 DTO에 대응하는 TS 타입 (`ApiResponse<T>`, `Page<T>`, Book/Review/Member/Subscription/Card 계열) | 수기로 백엔드와 동기화 |
| `mappers.ts` | DTO → UI 도메인 타입 변환 (`toBook`, `toMember`, `toCard`, `toReview`, `toPaged` 등) | 백엔드에 없는 필드는 임시 기본값 사용(`DEFAULT_RATING` 등) |
| `auth.js` | 로그인/회원가입 (`/auth/login`, `/auth/signup`), 로그인 후 게스트 장바구니 병합(`mergeCart`) | |
| `authStorage.js` | 토큰 localStorage 저장 | 섹션 3.1 참고 |
| `member.ts` | 내 정보(`/members/me`), 구독 상태(`/members/me/subscription`) | `unwrap()` 사용 |
| `mypage.js` | 마이페이지 전반: 프로필, 먹이 가능 도서, 독서노트, RAG 질의, 주문, 쿠폰, 반품, 재입고 요청, 리뷰 (`/mypage/**`) | `unwrap()` 사용 |
| `cards.ts` | 가상카드 목록/발급/수정 | **주의**: 경로가 `/members/me/cards`로 되어 있어 실제 백엔드 `/api/cards`, `/api/cards/me`, `/api/cards/{cardId}/status`와 다르다. `CardResponse` 타입도 `cardCompany`/`virtualBalance`/`isDefault` 필드와 `TERMINATED` 상태값을 갖는데, 실제 백엔드 `CardResponse`에는 이 필드들이 없고 상태값은 `CLOSED`다. 코드 주석상 "백엔드에 card 모듈이 아직 없어 목업 기반"이라고 명시되어 있어 — 백엔드 카드 API가 이미 존재하므로 계약 재정렬이 필요한 지점 |
| `cart.js` | 게스트 장바구니(localStorage `guest_cart`) + 로그인 시 서버 장바구니, 둘 다 `/cart` 경로 가정 | 백엔드 cart 모듈 미구현(TODO 주석), `unwrap()` 미사용(레거시 패턴으로 남겨둠) |
| `checkout.js` | 결제 요약(`/checkout/summary`) | `unwrap()` 미사용 |
| `wishlist.ts` | 찜 목록/추가/삭제(`/wishlist/me`, `/wishlist/{bookId}`), 최근 본 상품(`/recent-books/me`) | catalog-service 소유 데이터로 MSA 전환 시 경로가 `/members/me/*`에서 이동. `unwrap()` 사용 |
| `reviews.ts` | 리뷰 목록/작성/삭제(`/books/{bookId}/reviews`, `/reviews/{reviewId}`) | `unwrap()` 사용 |
| `books.ts` | 도서 목록/검색/베스트셀러/신간/상세/시놉시스(`/books/**`) | `unwrap()` 사용 |

**mock/실API 분기**: 각 파일이 개별적으로 `const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true"`를 선언하고, `true`면 `src/mocks/`의 목업 함수(`mockDelay`로 지연 시뮬레이션 포함)를, 아니면 `apiClient`를 호출한다. 파일 단위 분기라 전역 스위치는 없다.

**`unwrap()` 헬�터** (`client.ts`): `ApiResponse<T>` 껍데기를 벗겨 `data`만 반환. `success: false`면(HTTP 200이어도) `error.message` 또는 `message`로 `Error`를 던져 react-query 등 호출측이 실패로 인식하게 한다. 다만 `auth.js`, `cart.js`, `checkout.js`, `mypage.js` 일부는 이 헬퍼 없이 `res.data.data`를 직접 꺼내거나 응답을 그대로 반환하는 이전 패턴을 쓴다 — 코드 주석에 "이번 스코프 밖, 손대지 않음"으로 명시된 기존 부채.

**`client.ts` axios 설정**: `baseURL`은 `import.meta.env.VITE_API_BASE_URL` (없으면 `/api`). 요청 인터셉터 1개(Authorization 자동 첨부, 섹션 3.1). 응답 인터셉터는 없음 — 에러 처리는 각 호출부에서 axios 에러(`err.response?.data?.error?.message` 등)를 직접 파싱한다(`pages/Login.jsx`, `pages/Signup.jsx` 참고).

### 3.3 라우팅 구조 (`App.jsx`)

| 경로 | 컴포넌트 | 보호 여부 |
|---|---|---|
| `/` | `ProductListPage` | 아니오 |
| `/category` | `ProductListPage` | 아니오 |
| `/best` | `BestsellersPage` | 아니오 |
| `/new` | `NewReleasesPage` | 아니오 |
| `/books/:id` | `ProductDetailPage` | 아니오 |
| `/login` | `Login` | 아니오 |
| `/signup` | `Signup` | 아니오 |
| `/cart` | `Cart` | 아니오 (게스트 장바구니 지원) |
| `/checkout` | `Checkout` | **예** (`ProtectedRoute`) |
| `/mypage` | `MyPage` | **예** (`ProtectedRoute`) |
| `/wishlist` | `WishlistPage` | 아니오 |
| `/cards` | `CardsPage` | 아니오 |
| `/payment/kakao/success` | `KakaoPaySuccess` | **예** (`ProtectedRoute`) |
| `/payment/kakao/fail` | `KakaoPayFail` | **예** (`ProtectedRoute`) |
| `/payment/kakao/cancel` | `KakaoPayCancel` | **예** (`ProtectedRoute`) |

모든 라우트는 공통 `Layout`(헤더 + `Outlet`) 아래에 있으며, `Layout` 자체는 `QueryClientProvider` → `BrowserRouter` → `ToastProvider` → `AuthProvider` 순으로 감싸여 있다.

> `/wishlist`, `/cards`는 인증이 필요한 개인 데이터를 다루지만 현재 `ProtectedRoute`로 감싸여 있지 않다 — 백엔드 API 자체는 인증을 요구하므로(`/wishlist/me`, `/api/cards/**`) 비로그인 접근 시 API 호출 단계에서 401을 받게 되는 구조.

### 3.4 폴더 구조 개요 (`frontend/src/`)

| 폴더 | 역할 |
|---|---|
| `api/` | 백엔드 통신 계층 — axios 클라이언트, 도메인별 API 함수, DTO 타입, DTO→UI 매퍼 |
| `components/` | 재사용 UI 컴포넌트 (`Button`, `Modal`, `Toast`, `Header`, `ProtectedRoute`, `BookCard`, `SwipeDeck`, `LionCharacter` 등) |
| `context/` | React Context — `AuthContext`(인증 전역 상태)만 존재 |
| `mocks/` | `VITE_USE_MOCK=true`일 때 쓰는 목업 데이터/응답 함수 |
| `pages/` | 라우트 단위 페이지 컴포넌트. `payment/` 하위에 카카오페이 콜백 페이지(success/fail/cancel) |
| `types/` | UI 도메인 타입 (`book.ts`, `card.ts`, `member.ts`, `common.ts`) — `api/types.ts`(백엔드 DTO 타입)와 별개 계층 |
| `App.jsx` | 라우터 + 전역 프로바이더(QueryClient/Auth/Toast) 루트 구성 |
| `main.tsx` | 앱 엔트리포인트 |

---
