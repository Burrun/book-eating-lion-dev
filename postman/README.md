# Postman API 테스트

`backend/contracts/{catalog,member,order,ai}-v1.yaml` 에서 생성. 폴더는 서비스(모듈) 단위다.

## 파일

| 파일 | 용도 |
| --- | --- |
| `book-eating-lion.postman_collection.json` | 컬렉션 (요청 70여 개) |
| `a1-flex-tunnel.postman_environment.json` | 환경 — SSH 터널 기준 localhost |

Postman > Import > 두 파일 모두 넣고, 우측 상단에서 환경을 `a1-flex (SSH 터널)` 로 선택.

## 접속 준비

Docker 스택은 원격 서버(a1-flex)에 있고 8080은 외부에 열려 있지 않다. 터널을 먼저 뚫는다.

```powershell
# 게이트웨이만
ssh -N -L 8080:localhost:8080 a1-flex

# /internal/** 폴더까지 쓰려면 서비스 포트도 함께
ssh -N -L 8080:localhost:8080 -L 8081:localhost:8081 -L 8082:localhost:8082 -L 8083:localhost:8083 -L 8084:localhost:8084 a1-flex
```

로컬에서 직접 띄웠으면 터널 없이 그대로 된다.

## 실행 순서

```
1. 02 catalog > Books > GET 도서 목록     → 스모크. 200 이면 게이트웨이+catalog 정상
2. 01 member  > Auth  > POST 회원가입      → 409 면 이미 가입된 것 (정상)
3. 01 member  > Auth  > POST 로그인        → 🔴 필수. accessToken 을 컬렉션 변수에 저장
4. 01 member  > Cards > POST 카드 발급     → cardId 저장
5. 03 order   > Cart  > POST 담기 → Orders > POST 주문 생성
6. 02 catalog > Reviews > POST 리뷰 작성   → 구매확정 후에야 통과 (아니면 403 이 정상)
7. 04 ai      > Lion  > GET 내 사자 상태
```

3번 로그인이 `{{accessToken}}` 을 채우고, 컬렉션 레벨 Bearer 인증이 나머지 요청에 자동으로 붙는다. 공개 조회 요청만 `noauth` 로 덮어써 뒀다.

Collection Runner 로 폴더 단위 일괄 실행도 된다 (요청 순서가 위 흐름대로 정렬돼 있다).

## 자동 저장되는 변수

| 변수 | 채워지는 요청 |
| --- | --- |
| `accessToken` / `refreshToken` | 로그인, 토큰 재발급 |
| `memberId` | 회원가입, GET 내 정보 |
| `bookId` | GET 도서 목록 (첫 항목) |
| `cardId` | 카드 발급, GET 내 카드 |
| `cartItemId` | 장바구니 조회/담기 |
| `orderId` | 주문 생성 |
| `reviewId` / `inquiryId` / `faqId` / `categoryId` | 각 생성 요청 |
| `memberCouponId` | GET 보유 쿠폰 |
| `chatTicket` | 채팅 티켓 발급 |

## 공통 테스트

모든 요청에 컬렉션 레벨 테스트가 붙는다.

- 5xx 면 실패
- HTTP 200 이어도 `success: false` 면 콘솔에 `error` 를 찍는다 (백엔드가 논리 실패를 이렇게 표현한다)

## 알아둘 것

- **`/internal/**` 은 게이트웨이에 라우팅 규칙이 없다.** 의도된 것이다 — 서비스 간 전용 경로라 외부에서 도달하면 안 된다. 그래서 `99 · internal` 폴더만 `{{baseUrl}}` 이 아니라 `{{orderUrl}}` / `{{memberUrl}}` 을 쓴다.
- **`paymentMethod` enum 은 `VIRTUAL_CARD` | `KAKAO_PAY`** 다. `BANK_TRANSFER` 는 없다.
- **RAG 질의 필드는 `query`** 다. `question` 이 아니다.
- **`mode: "answer"` 는 Bedrock 실호출 = 요청마다 과금**된다. `AI_DAILY_QUOTA`(기본 50)에 걸린다. 구조만 볼 거면 `mode: "search"` 를 쓸 것 — LLM 을 안 부른다.
- **리뷰 작성은 구매 권한이 있어야 통과**한다. order 가 구매확정 시 `ReviewPermissionGranted` 이벤트(Redis Stream)로 권한을 미리 넘긴다. 주문 없이 부르면 403 이 정상이다.
- **Admin 폴더는 `role=ADMIN`** 이 필요하다. 가입 직후 계정은 `USER` 라 403 이 난다.
- **WebSocket(`/ws/ai/chat`)은 이 컬렉션에 없다.** Postman 에서 WebSocket 은 별도 요청 타입이라 HTTP 컬렉션에 못 넣는다. `POST /api/ai/bot/chat/ticket` 으로 티켓을 받은 뒤 `ws://localhost:8080/ws/ai/chat?ticket=...` 로 따로 연결할 것.

## 계약에서 확인 못 한 바디

아래 하나는 계약 YAML 에 요청 스키마가 명시돼 있지 않아 추정으로 채웠다. 400 이 나면 응답 `error` 로 실제 필드명을 확인하고 고칠 것.

- `POST /api/catalog/books/{bookId}/inquiries` — `{title, content, secret}` 로 가정
