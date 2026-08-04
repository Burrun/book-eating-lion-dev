# 인증/회원 도메인 API 설계 (BOO-5)

- Date: 2026-08-04
- Scope: `backend/modules/member`, `backend/modules/common`(ApiResponse 확장), `backend/apps/api`(SecurityConfig·의존성·설정), `db/1_demo_data.sql`
- Status: Approved

## 배경

`auth`/`member` 도메인 6개 엔드포인트(회원가입, 로그인, 토큰 재발급, 내 정보 조회/수정, 회원 등급 조회)를 AWS Cognito를 Identity Provider로 사용하는 OAuth2 Resource Server 방식으로 구현한다. `modules/member`는 이전까지 `Member(id, email, name)` 엔티티와 `MemberRepository`(빈 인터페이스), 그리고 존재하지 않는 `findByUsername`을 호출하는 깨진 `MemberService` 스텁만 있는 상태였다(`190abf1` 베이스 커밋 이후 미완성으로 방치됨).

## 조사 결과 (기존 코드/이력 확인)

- `common.dto.ApiResponse`가 book 도메인 전역의 표준 응답 포맷(`success/message/data`)으로 이미 채택되어 있다(`docs/superpowers/specs/2026-08-03-book-domain-api-design.md`). 본 작업에서 요구되는 구조화된 에러 코드(`error.code`, `error.message`)를 담기 위해, 기존 정적 팩토리(`success(data)`, `success(message,data)`, `error(message)`)의 시그니처/동작은 그대로 두고 `ErrorDetail error` 필드와 `error(code, message)` 팩토리를 추가하는 **하위 호환 확장**으로 처리했다. book 도메인 호출부는 무변경.
- `feature/BOO-05-user-login` 브랜치에 동일 작업을 2차례 시도한 이력(PR #4, #6)이 있었으나 로컬 JWT 발급 + username/password + BCrypt 방식(Cognito 미연동)이었고, book/order 도메인 작업 이전 지점에서 갈라져 나와 있었으며, 두 PR 모두 작성 직후(15~20분 내) 작성자 본인이 close했다. 재사용하지 않고 현재 `main` 기준으로 새로 구현했다.
- 프로젝트 전체에 `SecurityFilterChain` 빈이 없었다(book/order 컨트롤러는 `X-Member-Id` 헤더로 회원을 식별하는 임시 방식). 이번에 추가하는 `SecurityConfig`는 `/api/members/me/**`만 인증을 요구하고 나머지 전부 `permitAll`로 두어 book/order의 기존 동작을 깨지 않는다.
- 실제 AWS Cognito User Pool 자격 증명이 없어 모든 Cognito 관련 설정값은 환경변수 플레이스홀더(`${AWS_COGNITO_*}`)로 구성했다. 실제 값이 채워지기 전까지 `spring.security.oauth2.resourceserver.jwt.issuer-uri`를 통한 `JwtDecoder` 자동 구성이 로컬 기동 시점에 실패할 수 있다(정상적인 클라우드 의존성이며, 이번 범위에서 e2e 실행 검증은 하지 않는다).

## 1. `Member` 엔티티 & DB 스키마 변경

`modules/member/src/main/java/com/bookeatinglion/member/domain/Member.java`:

| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK, AUTO_INCREMENT | 기존 |
| cognitoSub | String | NOT NULL, UNIQUE | 신규, Cognito `sub` 클레임과 매핑 |
| email | String | NOT NULL, UNIQUE | 기존 |
| name | String | NOT NULL | 기존, PATCH 대상 |
| phoneNumber | String | NULL 허용 | 신규, PATCH 대상 |
| gender | Gender(기존 enum) | NULL 허용 | 신규 사용, PATCH 대상 |
| birthDate | LocalDate | NULL 허용 | 신규, PATCH 대상 |
| role | Role(기존 enum, USER/ADMIN) | NOT NULL, default USER | 신규 사용 |
| grade | MemberGrade(신규 enum: BRONZE/SILVER/GOLD/VIP) | NOT NULL, default BRONZE | 신규 |
| point | int | NOT NULL, default 0 | 신규 |

비밀번호는 로컬 DB에 저장하지 않는다(Cognito가 자격증명을 관리). `Member.register(cognitoSub, email, name)` 정적 팩토리로 신규 가입 시 엔티티를 생성하고, `updateProfile(name, phoneNumber, gender, birthDate)`로 부분 수정한다(null 필드는 미변경).

포인트 적립/등급 승급 로직(주문 완료 연동 등)은 book 도메인의 `salesCount`와 동일하게 **범위 밖**이다 — `grade`/`point`는 조회만 가능하고 갱신 경로는 이번에 만들지 않는다.

`db/1_demo_data.sql`의 `members` `CREATE TABLE`/`INSERT`를 위 스키마에 맞게 갱신했다(NOT NULL 컬럼 추가에 따른 기존 데모 INSERT 수정 포함).

## 2. DTO (`member.dto` 패키지, Java record)

- `SignupRequest(email, password, name)` / `SignupResponse(memberId, email, name)`
- `LoginRequest(email, password)` / `RefreshRequest(refreshToken)`
- `TokenResponse(accessToken, refreshToken, tokenType, expiresIn)`
- `MemberResponse(id, email, name, phoneNumber, gender, birthDate, role, grade, point)` — `from(Member)`
- `MemberUpdateRequest(name, phoneNumber, gender, birthDate)` — 부분 수정, null 필드는 미변경
- `MemberGradeResponse(grade, point)` — `from(Member)`

## 3. Cognito 연동 (`member.infra.cognito.CognitoAuthClient`)

`CognitoIdentityProviderClient`(AWS SDK v2)를 감싸는 컴포넌트:

- `signUp(email, password, name)`: `AdminCreateUserRequest`(이메일 인증 메시지 SUPPRESS) + `AdminSetUserPasswordRequest(permanent=true)` → 응답의 `sub` 속성 반환. `UsernameExistsException` → `CognitoAuthException("DUPLICATE_EMAIL", ...)`.
- `login(email, password)`: `AdminInitiateAuthRequest`(`ADMIN_USER_PASSWORD_AUTH`) → `AuthenticationResultType`. `NotAuthorizedException`/`UserNotFoundException` → `CognitoAuthException("INVALID_CREDENTIALS", ...)`.
- `refresh(refreshToken)`: `AdminInitiateAuthRequest`(`REFRESH_TOKEN_AUTH`). Cognito는 이 플로우에서 새 refresh token을 돌려주지 않으므로, `AuthService`가 기존 refresh token을 그대로 유지한다.
- App Client Secret이 설정된 경우(`app.cognito.client-secret`) `SECRET_HASH`(HmacSHA256)를 로그인 요청에 포함한다. refresh 플로우의 SECRET_HASH는 범위 밖(대부분의 dev 클라이언트는 secret 없이 구성).

설정은 `app.cognito.{region,user-pool-id,client-id,client-secret}` (`CognitoProperties`) + `spring.security.oauth2.resourceserver.jwt.issuer-uri`이며, 전부 `application-local.yml`에서 환경변수로 주입한다(`.env.example`에 변수명 추가).

## 4. Service

- `AuthService`(`CognitoAuthClient` + `MemberRepository`): `signup`은 Cognito 호출 전에 `existsByEmail`로 먼저 중복 검사(Cognito에 orphan 유저가 생기는 것을 방지), 성공 시 `Member.register`로 로컬 동기화. `login`/`refresh`는 Cognito 토큰 응답을 `TokenResponse`로 변환.
- `MemberService`(`MemberRepository`): `getMyProfile`/`updateProfile`/`getGrade` 전부 `cognitoSub` 기준으로 조회하며, 없으면 `MemberNotFoundException`.

## 5. Controller

- `AuthController`(`/api/auth`, 인증 불필요): `POST /signup`, `POST /login`, `POST /refresh`
- `MemberController`(`/api/members`, 인증 필요): `GET /me`, `PATCH /me`, `GET /me/grade` — `@AuthenticationPrincipal Jwt jwt`의 `jwt.getSubject()`(Cognito `sub`)로 회원을 식별한다.

두 컨트롤러 모두 book의 `BookController` 패턴처럼 `ApiResponse<T>`를 직접 반환한다(200 OK 고정).

## 6. SecurityConfig (`apps/api/.../config/SecurityConfig.java`)

- Stateless, CSRF 비활성화
- `/api/members/me/**` → `authenticated()`, 나머지 전부 `permitAll()`
- `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` — `issuer-uri` 프로퍼티 기반 자동 `JwtDecoder`

## 7. 예외 처리

`member.exception.MemberException`(추상 베이스, `code` 보유) → `MemberNotFoundException`(404, `MEMBER_NOT_FOUND`), `DuplicateEmailException`(409, `DUPLICATE_EMAIL`), `CognitoAuthException`(코드별 401/409/400).
`member.controller.MemberExceptionHandler`(`@RestControllerAdvice(basePackages = "com.bookeatinglion.member.controller")`, `BookExceptionHandler`와 동일한 패턴)가 이들을 `ApiResponse.error(code, message)`로 변환.

## 8. 테스트

book 모듈 컨벤션을 그대로 따른다(`MemberModuleTestApplication` + `@DataJpaTest`/`@WebMvcTest` 슬라이스):

- `MemberRepositoryTest`(H2): `findByCognitoSub`/`findByEmail`/`existsByEmail`
- `AuthServiceTest`, `MemberServiceTest`(Mockito 단위 테스트): 골든 패스 + 중복 이메일/회원 없음 예외 케이스
- `AuthControllerTest`(`addFilters=false`로 시큐리티 필터 우회 — 공개 엔드포인트), `MemberControllerTest`(필터를 켜둔 채 `spring-security-test`의 `jwt()` 요청 후처리기로 인증 주체 주입 — `addFilters=false`와 `jwt()`를 함께 쓰면 `@AuthenticationPrincipal`이 null로 resolve되는 것을 확인하여 이 조합만 필터를 켜서 사용함)

## 비범위 (Out of scope)

- 등급 승급/포인트 적립 로직(주문 완료 연동 등)
- Cognito 이메일 인증(가입 시 `AdminCreateUser`로 인증 절차를 생략하고 즉시 활성 계정 생성)
- refresh 플로우의 SECRET_HASH 처리
- book/order 컨트롤러를 `X-Member-Id` 헤더 방식에서 JWT 기반으로 이관하는 작업
- 실제 AWS Cognito User Pool 프로비저닝(Terraform/콘솔) 및 e2e 실행 검증 — 코드는 환경변수로 구성 가능하도록 준비만 함
- CORS 설정
