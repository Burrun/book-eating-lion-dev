# 인증/회원 API (BOO-5) Implementation Plan

**Goal:** `modules/member`의 깨진 스텁(`MemberService.findByUsername`이 존재하지 않는 리포지토리 메서드를 호출)을 제거하고, AWS Cognito를 Identity Provider로 사용하는 인증/회원 6개 API(회원가입/로그인/토큰재발급/내정보조회/내정보수정/등급조회)를 구현한다.

**Architecture:** Cognito Admin API(`CognitoIdentityProviderClient`) → `AuthService`/`MemberService`(엔티티↔DTO 매핑, 트랜잭션) → `@RestController` → `common.dto.ApiResponse<T>`(확장된 `error{code,message}` 포함) JSON 응답. Spring Security OAuth2 Resource Server가 `Authorization: Bearer <Cognito JWT>`를 검증하고 `@AuthenticationPrincipal Jwt`의 `sub` 클레임으로 회원을 식별한다.

**Tech Stack:** Spring Boot 3.4.2, Java 21, Spring Security OAuth2 Resource Server, AWS SDK v2 `cognitoidentityprovider`, Spring Data JPA, Lombok, JUnit 5 + Mockito + MockMvc, H2(테스트 전용)

**Reference:** 설계 문서 `docs/superpowers/specs/2026-08-04-auth-member-api-design.md` (사용자 승인 완료)

## Global Constraints

- 모든 응답은 확장된 `common.dto.ApiResponse<T>`(`success(data)` / `error(message)` / `error(code, message)`)로 감싼다. 기존 book 도메인 호출부는 무변경.
- `member` 도메인 파일만 생성/수정한다. book/order 컨트롤러의 `X-Member-Id` 헤더 방식은 건드리지 않는다.
- `SecurityConfig`(`apps/api`)는 `/api/members/me/**`만 `authenticated()`, 나머지는 `permitAll()` — book/order가 아직 인증 연동 전이므로 기존 동작 보존.
- Cognito 자격 증명(User Pool ID/Client ID/Secret/Region)은 실제 리소스가 없어 환경변수 플레이스홀더로만 구성한다.

---

## File Structure (실제 구현 결과)

```
backend/modules/common/
  build.gradle                                                (수정: oauth2-resource-server 추가)
  src/main/java/com/bookeatinglion/common/dto/
    ApiResponse.java                                          (수정: error 필드 추가)
    ErrorDetail.java                                          (신규)

backend/modules/member/
  build.gradle                                                (수정: cognito SDK, h2, security-test)
  src/main/java/com/bookeatinglion/member/
    domain/Member.java                                        (수정: 필드 확장)
    domain/MemberGrade.java                                   (신규)
    repository/MemberRepository.java                          (수정: 쿼리 메서드 추가)
    dto/{SignupRequest,SignupResponse,LoginRequest,RefreshRequest,TokenResponse,
         MemberResponse,MemberUpdateRequest,MemberGradeResponse}.java  (신규)
    config/{CognitoProperties,CognitoClientConfig}.java        (신규)
    infra/cognito/CognitoAuthClient.java                       (신규)
    exception/{MemberException,MemberNotFoundException,
               DuplicateEmailException,CognitoAuthException}.java     (신규)
    service/{AuthService,MemberService}.java                   (AuthService 신규, MemberService 전면 교체)
    controller/{AuthController,MemberController,
                MemberExceptionHandler}.java                   (신규)
  src/test/java/com/bookeatinglion/member/
    MemberModuleTestApplication.java                           (신규)
    repository/MemberRepositoryTest.java                       (신규)
    service/{AuthServiceTest,MemberServiceTest}.java           (신규)
    controller/{AuthControllerTest,MemberControllerTest}.java  (신규)

backend/apps/api/
  src/main/java/com/bookeatinglion/api/config/SecurityConfig.java   (신규)
  src/main/resources/application-local.yml                    (수정: cognito/resourceserver 설정)

db/1_demo_data.sql                                             (수정: members 스키마/INSERT 갱신)
.env.example                                                   (수정: AWS_COGNITO_* 변수 추가)
```

---

### Task 1: `common.dto.ApiResponse` 확장 — [x] 완료

`ErrorDetail(code, message)` record 추가, `ApiResponse`에 `error` 필드 + `error(code, message)` 팩토리 추가. 기존 정적 팩토리 시그니처/동작은 그대로 유지(book 도메인 무변경 확인: `new ApiResponse(...)` 직접 호출부가 코드베이스에 없음을 grep으로 확인).

### Task 2: 의존성 추가 — [x] 완료

`common/build.gradle`에 `spring-boot-starter-oauth2-resource-server`(book/order에도 전파). `member/build.gradle`에 `software.amazon.awssdk:cognitoidentityprovider:2.25.20`, 테스트용 `h2`, `spring-security-test`.

### Task 3: `Member` 엔티티 + `MemberGrade` enum — [x] 완료

`cognitoSub`/`phoneNumber`/`gender`/`birthDate`/`role`/`grade`/`point` 필드 추가. `Member.register(cognitoSub, email, name)` 정적 팩토리, `updateProfile(...)` 부분 수정 메서드.

### Task 4: `MemberRepository` — [x] 완료

`findByCognitoSub`, `findByEmail`, `existsByEmail` 추가(기존 컴파일 에러였던 `findByUsername` 제거).

### Task 5: DTO — [x] 완료

`member.dto` 패키지에 8개 record 추가(설계 문서 §2 참고).

### Task 6: Cognito 연동 — [x] 완료

`CognitoProperties`(`@ConfigurationProperties(prefix="app.cognito")`), `CognitoClientConfig`(`CognitoIdentityProviderClient` 빈), `CognitoAuthClient`(signUp/login/refresh, SECRET_HASH 계산, Cognito 예외 → `CognitoAuthException` 매핑). `application-local.yml`/`.env.example`에 환경변수 플레이스홀더 추가.

### Task 7: `SecurityConfig` — [x] 완료

`apps/api/.../config/SecurityConfig.java`: stateless, `/api/members/me/**`만 인증 요구, `oauth2ResourceServer(jwt)`.

### Task 8: Service 계층 — [x] 완료

`AuthService`(signup 시 `existsByEmail` 선검증 → Cognito 호출 → 로컬 동기화; login/refresh는 토큰 변환), `MemberService`(기존 깨진 스텁 전면 교체: `getMyProfile`/`updateProfile`/`getGrade`).

### Task 9: 예외 처리 — [x] 완료

`MemberException` 추상 베이스 + 3개 구체 예외, `MemberExceptionHandler`(`@RestControllerAdvice(basePackages="com.bookeatinglion.member.controller")`, `BookExceptionHandler`와 동일 패턴).

### Task 10: Controller — [x] 완료

`AuthController`(`/api/auth/{signup,login,refresh}`), `MemberController`(`/api/members/me`, `/api/members/me/grade`).

### Task 11: 데모 데이터 — [x] 완료

`db/1_demo_data.sql`의 `members` 테이블에 신규 컬럼 추가, 시드 INSERT를 NOT NULL 컬럼(`cognito_sub`, `role`, `grade`)에 맞게 갱신.

### Task 12: 테스트 — [x] 완료

`MemberModuleTestApplication` + `MemberRepositoryTest`(H2) + `AuthServiceTest`/`MemberServiceTest`(Mockito) + `AuthControllerTest`/`MemberControllerTest`(WebMvcTest). `MemberControllerTest`는 `@AutoConfigureMockMvc(addFilters=false)`를 쓰지 않는다 — 필터를 꺼두면 `spring-security-test`의 `jwt()` 후처리기가 채운 인증 정보가 `@AuthenticationPrincipal`에 전달되지 않아 NPE가 발생함을 실제로 확인(디버깅 기록: 최초 구현 시 3개 테스트가 NPE로 실패 → 필터를 켜자 통과).

### Task 13: 빌드 검증 — [x] 완료

```bash
cd backend
./gradlew :modules:member:test   # PASS (19 tests)
./gradlew build                  # BUILD SUCCESSFUL — book/order 회귀 없음, apps:api bootJar 포함 전체 컴파일 확인
```

---

## Out of Scope (구현하지 않음)

- 등급 승급/포인트 적립 로직(주문 완료 연동)
- Cognito 이메일 인증 절차(가입 시 즉시 활성화)
- refresh 플로우 SECRET_HASH
- book/order 컨트롤러의 `X-Member-Id` → JWT 전환
- 실제 Cognito User Pool 프로비저닝 및 curl 기반 e2e 검증(실 AWS 자격 증명 없음)
- CORS 설정
