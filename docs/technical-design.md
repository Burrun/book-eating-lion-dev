# 설계 상세

[프로젝트 소개](../README.md)

## 서비스 간 호출과 이벤트

```mermaid
flowchart LR
    CF[CloudFront] --> ALB[ALB / Ingress]
    ALB --> CAT[catalog-service]
    ALB --> ORD[order-service]
    ALB --> MEM[member-service]
    ALB --> RAG[ai-rag]
    ALB --> BOT[ai-bot]

    CAT -- "Feign · 재고 조회/입고" --> ORD
    ORD -- "Feign · 도서 가격" --> CAT
    ORD -- "Feign · 가상카드/구독" --> MEM
    CAT -- "Feign · 구독/알림 프로필" --> MEM
    RAG -- "Feign · 구독 여부" --> MEM
    CAT -- "Feign · 추천 순위" --> RAG

    ORD -. "Redis Streams · 리뷰 권한 / 재입고" .-> CAT
    CAT -. "Redis Streams · 추천 인덱스" .-> RAG
    ORD == "SQS · 구매 확정" ==> RAG
    CAT == "SQS · 신간 EPUB 인제스트" ==> RAG

    RAG --> BR[(Bedrock)]
    RAG --> VEC[(S3 Vectors)]
    BOT --> BR
```

실선은 동기(OpenFeign + Resilience4j), 점선은 Redis Streams, 굵은 선은 SQS다. 구매 확정, 신간 인제스트, 추천 인덱스 갱신 이벤트는 DB 커밋 후에 발행한다. 재입고 이벤트의 예외는 [이벤트 발행 트러블슈팅](troubleshooting-event-publishing.md)에 정리했다.

## 서비스 구성

**서비스 4개 / Deployment 5개**. `ai-service`는 이미지 1개를 Deployment 2개(`ai-rag`, `ai-bot`)로 띄운다 — 두 워크로드의 확장 기준과 타임아웃 정책이 다르기 때문이다.

| 서비스 | 담당 도메인 | 포트 | DB 스키마 (전용 계정) | 외부 의존 | HPA (dev / prod) |
| --- | --- | --- | --- | --- | --- |
| `catalog-service` | 도서·카테고리, 리뷰·찜·최근 본 책, eBook 열람·하이라이트, 재입고 알림, FAQ·상품문의, 추천 카드 | 8081 | `catalog_db` (`catalog_svc`) — **writer/reader 분리** | S3(eBook), SES, SQS 발행 | 2 → 6 / 10 · CPU 70%·Mem 80% |
| `order-service` | 주문·결제(가상카드, 카카오페이)·배송, **재고(inventory)**, 장바구니, 쿠폰 | 8082 | `order_db` (`order_svc`) | Redisson 분산 락, KakaoPay, SQS 발행 | 2 → 8 / 30 · CPU 70% |
| `member-service` | 회원·인증(Cognito), 배송지, 가상카드, 구독 | 8083 | `member_db` (`member_svc`) | Cognito | 2 → 6 · CPU 70% |
| `ai-service` (`ai-rag`) | 열람 가능한 책 RAG(`/api/ai/lion`), 라이언 육성, 의미 기반 추천 | 8084 | `ai_db` (`ai_svc`) + S3 Vectors | Bedrock, S3 Vectors, SQS 소비 | 1 → 6 · CPU 70% |
| `ai-service` (`ai-bot`) | 문의봇(FAQ 근거 LLM), 1:1 상담 채팅(WebSocket) | 8084 | `ai_db` + Redis(상담방 상태) | Bedrock | 1 → 10 · 동시 요청 수 (구성 미완료) |

> `ai-bot`의 요청 수 기반 HPA는 메트릭 공급 구성이 미완료다. 상태와 필요한 작업은 [배포·확장 트러블슈팅](troubleshooting-deploy-scaling.md)에 정리했다.

**DB 격리는 클러스터가 아니라 계정 권한으로 만든다.** PostgreSQL 인스턴스 하나에 스키마 4개, 스키마마다 전용 계정이 소유자다. `catalog_svc`로 `order_db`를 조회하면 `permission denied for schema order_db`다.

### 인프라 구성 요소 (integrated 환경 기준)

| 구성 요소 | 스펙 | 쓰는 곳 |
| --- | --- | --- |
| RDS PostgreSQL 16 | `db.t4g.micro` writer 1 + **read replica 1**, 앞단 **RDS Proxy** | 클러스터 1개 / 스키마 4개 / 계정 4개 |
| ElastiCache Valkey 8.2 | `cache.t4g.medium`, **TLS 강제**(`transit_encryption_enabled`) | 분산 락, 캐시, Streams, Pub/Sub, 쿼터 |
| Amazon SQS | `ai-ingest`(visibility 300s), `book-purchase-queue`(60s) + 각 DLQ(14일 보관, `maxReceiveCount=3`) | 유실 불가 도메인 이벤트 |
| Amazon SNS | 알람 토픽 1개 → 이메일 구독 | CloudWatch 알람(DB 커넥션·CPU·메모리, Valkey 메모리, Pod CPU) |
| Amazon Bedrock | 임베딩 `titan-embed-text-v2`(1024차원) · RAG `Claude Haiku 4.5` · 봇 `Nova Micro` | ai-service |
| S3 Vectors | `wiki-v1`(책 본문), `recommendation-books-v1`(추천) — cosine | ai-service |
| Amazon Cognito | User Pool (서버 사이드 `AdminInitiateAuth`) | member-service, 전 서비스 JWT 검증 |

### 라우팅 (Ingress)

| 경로 | 서비스 |
| --- | --- |
| `/api/catalog/**` | catalog-service |
| `/api/orders/**` `/api/cart/**` `/api/coupons/**` `/api/payments/**` | order-service |
| `/api/members/**` `/api/auth/**` `/api/cards/**` | member-service |
| `/api/ai/lion/**` | ai-rag |
| `/api/ai/bot/**` `/ws/ai/chat` | ai-bot |

### 코드 구조와 경계

```text
backend/
├── contracts/          # OpenAPI 4종 — 서비스 간 계약의 기준 명세
├── apps/               # 배포 단위 = bootJar = 컨테이너 이미지 (Feign·AWS SDK·설정 배선)
│   ├── catalog-api/  order-api/  member-api/  ai-api/
└── modules/            # 도메인 로직(라이브러리 jar). 서로 의존 금지
    ├── common/         # BaseEntity, 응답/예외, 이벤트 계약(Stream 키), Redis 공통 설정
    ├── book/  order/  member/  ai/
```

모노레포를 유지하는 대신 경계를 **기계적으로 두 번** 강제한다.

1. **CI** (`backend-ci.yml` › 모듈 경계 규칙): `apps/X-api`는 `common` + 자기 도메인 모듈만, `modules/*`는 `common`만 참조할 수 있다. 위반하면 빌드 실패.
2. **CODEOWNERS**: 도메인 디렉터리별로 리뷰 소유 팀을 지정한다(계약 `contracts/`는 백엔드 전원).

외부 연동은 가능한 한 포트로 분리해 구현체를 `apps`에 둔다 — 예: `BookPurchasePublisher` → `SqsBookPurchasePublisher`, `EbookStoragePort` → S3/로컬 어댑터, `VectorSearchPort` → `S3VectorSearchAdapter`, `InventoryPort` → Feign 어댑터. 도메인 모듈이 SQS·S3 SDK를 모르게 하고, 로컬에서는 어댑터만 바꿔 끼운다.

---

## 주요 백엔드 로직

### 4-1. 구매 이벤트를 커밋 후에 발행한다

트랜잭션 안에서 이벤트를 먼저 보내면, 그 뒤 단계(쿠폰 사용 확정, 배송 생성의 UNIQUE 충돌 등)에서 롤백이 나도 **이미 나간 이벤트는 되돌릴 수 없다.** 리뷰 권한 이벤트가 한때 커밋 전에 나가는 구조였고, 실제로 이벤트 발행 뒤에 주문이 롤백되는 결제 장애(2026-08-27)가 있었다. 리뷰 권한은 eBook 열람 권한을 겸하므로 새어 나가면 **결제되지 않은 책을 읽을 수 있게 된다**([트러블슈팅](troubleshooting-event-publishing.md)).

```mermaid
sequenceDiagram
    participant U as 사용자
    participant O as order-service
    participant DB as order_db
    participant R as Redis Streams
    participant Q as SQS book-purchase-queue
    U->>O: 결제 확정 (카드 / 카카오 approve)
    O->>DB: 재고 차감 · 결제 · 쿠폰 · 배송 (한 트랜잭션)
    Note over O: afterCommit 훅에 발행 예약만 해 둔다
    DB-->>O: COMMIT
    O-)R: ReviewPermissionGranted (try/catch)
    O-)Q: BookPurchase (try/catch)
    O-->>U: 200 OK (발행 실패와 무관)
```

다음 발행 지점에는 커밋 후 처리 방식을 적용했다.

| 발행 지점 | 채널 | 방식 |
| --- | --- | --- |
| `OrderService.publishPurchaseConfirmed` | Redis Streams + SQS | `TransactionSynchronization.afterCommit` |
| `OrderService.activateSubscriptionIfOrdered` | Feign(member) | `afterCommit` — 트랜잭션 안에서 실패하면 **돈은 나갔는데 주문은 미결제**로 롤백된다 |
| `AdminBookService.publishIngestEvent` | SQS | `afterCommit` |
| `BookRecommendationIndexPublisher` | Redis Streams | `@TransactionalEventListener(AFTER_COMMIT)` |
| `BookPurchaseHandler` / `FeedService` (캐시 갱신) | Redis Set | `afterCommit` — 롤백됐는데 캐시에만 남는 불일치 방지 |

발행 규칙 세 가지:

1. **채널마다 따로 `try/catch`** — 한 `Runnable` 안에서 Redis 발행이 던지면 뒤의 SQS 발행이 아예 안 나간다.
2. **예외를 호출자에게 올리지 않는다** — 커밋은 이미 끝났다. 던져 봐야 사용자에게 500만 보이고 되돌릴 것은 없다. 대신 `memberId`/`bookId`를 담은 ERROR 로그를 남긴다.
3. **소비 측은 전부 멱등** — at-least-once 전제. 리뷰 권한은 PK `(memberId, orderItemId)` 중복 확인, 구매는 PK `(memberId, bookId)`, 재입고는 `processed_restock_events`, 인제스트는 결정적 벡터 키 + delete-then-put.

> 관리자 재입고 이벤트의 커밋 전 발행 문제는 [이벤트 발행 트러블슈팅](troubleshooting-event-publishing.md)에 미해결 항목으로 남겨 두었다.

### 4-2. 메시징 채널: SQS · Redis Streams · Redis Pub/Sub · SNS

채널은 **"이 장애가 전이돼도 되는가"**로 고른다. 지금 이 응답에 값이 필요할 때만 동기(OpenFeign + Resilience4j)로 묻고, 나머지는 비동기로 미리 넘기거나 아예 묻지 않는다. 예컨대 리뷰 작성 시 "구매했나?"를 order에 묻지 않는다 — 구매 확정 시점에 order가 `ReviewPermissionGranted`로 권한을 미리 넘겨 두므로 **order-service가 죽어도 리뷰 작성은 동작한다.**

| 채널 | 전달 보장 | 이 프로젝트에서 쓰는 곳 | 고른 이유 |
| --- | --- | --- | --- |
| **SQS + DLQ** | at-least-once, 재시도, DLQ 보관 | 구매 확정(order→ai), 신간 EPUB 인제스트(catalog→ai) | 유실되면 **에러 없이 "근거를 못 찾았습니다"만 나온다.** 조용히 틀리는 이벤트는 큐가 보관해야 한다 |
| **Redis Streams + 컨슈머 그룹** | 컨슈머 그룹 내 분배, 재처리 가능 | 리뷰 권한, 재입고 알림, 추천 인덱스 갱신 | 여러 파드가 메시지를 나눠 처리하며, 중복 처리는 소비 측에서 막는다. 이미 쓰는 Redis라 Kafka를 새로 들이지 않았다 |
| **Redis Pub/Sub** | at-most-once, **모든 구독 파드**에 팬아웃 | 1:1 상담 채팅 | 그 방의 소켓을 쥔 모든 파드가 받아야 한다. 컨슈머 그룹을 쓰면 엉뚱한 파드 하나로 가서 조용히 버려진다 |
| **SNS** | 알림 팬아웃 | CloudWatch 알람 → 이메일 | 애플리케이션 이벤트가 아니라 **운영 알림 전용**이다 |

**이벤트 목록**

| 이벤트 | 발행 → 소비 | 채널 / 키 | 멱등 처리 |
| --- | --- | --- | --- |
| 구매 확정 | order → ai | SQS `book-purchase-queue` (10건 배치) | `purchased_books` PK |
| 신간 EPUB 등록 | catalog → ai | SQS `ai-ingest` (1건씩) | 원본 SHA-256 비교 + 결정적 벡터 키 |
| 리뷰 권한 발급 | order → catalog | Stream `events:review-permission-granted` / 그룹 `catalog-service` | `review_permissions` PK |
| 재입고 | order → catalog | Stream `events:inventory-restocked` / 그룹 `catalog-restock-alerts` | `processed_restock_events` |
| 추천 인덱스 갱신 | catalog → ai | Stream `events:catalog:recommendation-books` / 그룹 `ai-recommendation-indexer` | 실패 시 ACK 안 함 → pending 보존 |

**SQS 소비 규칙** (`SqsWorker`)

- 핸들러가 **정상 반환했을 때만** 메시지를 삭제한다. 예외면 남겨 두고, 가시성 시간 후 재배달 → `maxReceiveCount` 초과 시 DLQ(14일 보관).
- 폴링 예외로 루프를 끝내지 않는다 — 파드는 살아 있는데 소비만 멈추는 게 가장 늦게 발견되는 고장이다.
- 인제스트는 **1건씩**(임베딩 스로틀링), 구매는 **10건 배치**(DB 1행 + Redis 1행이라 가벼움).
- 로컬에서는 `SQS_*_ENABLED=false`가 기본이다 — 켜면 배포된 컨슈머와 실제 이벤트를 **경쟁**해서 가져간다.

**Redis 사용처 한눈에**

| 키 / 채널 | 용도 | 장애 시 |
| --- | --- | --- |
| `inventory:lock:{bookId}` | 재고 차감 분산 락 (Redisson) | 락 획득 실패 → 주문 거절 |
| `bestsellers::*` | 베스트셀러 캐시(TTL 30분) | DB 직접 조회 |
| `ai:purchased:{memberId}` / `ai:fed:{memberId}` | RAG 접근 권한 / 먹인 책 Set (TTL 24h) | **DB 원본으로 폴백**(fail-closed) |
| `ai:quota:{memberId}:{date}` | 일일 질의 상한 | **통과**(fail-open) + WARN |
| `catalog:recommendation:queue:{memberId}` | 스와이프 추천 카드 큐 | 키 삭제 후 다시 생성 |
| `events:*` | Redis Streams | 컨슈머 그룹 pending 보존 |
| 상담방 Hash/ZSet + Pub/Sub 채널 | 1:1 채팅 상태·전사(24h TTL, DB 미저장) | 방 휘발 |

### 4-3. 재고·결제 동시성

재고(`order_db.inventory`)는 상품이 아니라 **주문 서비스가 소유**한다. 애그리게잇 경계를 라이프사이클이 아니라 트랜잭션 경계로 정했고, 재고 차감과 주문·결제 상태 변경을 같은 DB 트랜잭션으로 처리한다. 외부 결제 API 호출은 DB 롤백으로 취소되지 않는다. 카탈로그는 재고를 `GET /internal/inventory?bookIds=` **벌크 조회**로만 읽는다(단건 API면 목록에서 N+1이 난다).

- **분산 락**: `InventoryLockExecutor`가 주문에 담긴 `bookId`를 **오름차순 정렬 후** 순서대로 락을 건다(`wait 5s / lease 10s`). `[1,2]`와 `[2,1]` 주문이 동시에 와도 락 획득 순서가 뒤바뀌는 것을 막는다.
- **결제 2종**
  - 가상카드: `createOrder` 한 트랜잭션에서 결제·재고 차감·쿠폰 확정·장바구니 정리·배송 생성까지 끝낸다.
  - 카카오페이: `createOrder`는 `ready`만 하고 `PENDING_PAYMENT`로 둔다. `approveKakaoPay`에서 **카카오 승인 API를 부르기 전에** 재고·쿠폰·중복 구독을 재검증한다 — 막히면 승인 요청 자체가 안 나가므로 재검증에서 거절된 요청은 외부 결제 승인으로 넘어가지 않는다.
- **비관적 락 보강**: 쿠폰(`MemberCoupon`)과 카카오 승인 대상 주문을 `SELECT … FOR UPDATE`로 읽는다. 낙관적 락만으로는 충돌이 커밋 시점에야 드러나는데, 그땐 이미 결제가 끝나 되돌릴 수 없다.
- **구독권 = 카탈로그의 도서 한 권(`book_id 9001`)**: 구독을 별도 상품 타입으로 만들지 않고 도서 한 행으로 표현해 가격 조회·주문·결제·주문 내역이 전부 기존 경로를 탄다. 결제 전 중복 구독을 검사하고(조회 실패는 503, fail-closed), 결제 확정 후 `afterCommit`에서 member-service에 활성화를 요청한다(멱등 엔드포인트).
- **Redisson 전용 커넥션**: 이벤트/캐시용 `RedisTemplate`과 분리했고, Redisson은 `spring.data.redis.ssl.enabled`를 자동으로 읽지 않아 `rediss://` 스킴을 직접 고른다.

### 4-4. DB 읽기/쓰기 분리 (catalog-service)

카탈로그는 읽기가 대부분이라 **writer / reader 커넥션을 나눈다.**

```text
@Transactional(readOnly = true) ──▶ reader 풀 (8) ──▶ Read Replica   (RDS Proxy 우회, 직결)
@Transactional                  ──▶ writer 풀 (3) ──▶ RDS Proxy ──▶ Primary
```

- `RoutingDataSourceConfig`: `AbstractRoutingDataSource`가 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`로 분기한다. 조회 서비스에는 이미 `readOnly = true`가 붙어 있어 손댈 곳이 거의 없었다.
- **`LazyConnectionDataSourceProxy`가 핵심이다.** 없으면 Spring이 트랜잭션 시작 *전에* 커넥션을 먼저 잡아서 readOnly 판단 시점이 지나가 버린다 — 에러 없이 **조용히 전부 writer로** 간다.
- Liquibase는 트랜잭션 밖에서 자기 커넥션을 쓰므로 자동으로 writer로 간다.
- `@Profile("prod")`에서만 켠다. 로컬은 표준 `spring.datasource` 하나로 뜬다.
- reader는 Proxy를 거치지 않으므로 `Pod 수 × reader 풀`이 리플리카의 `max_connections`를 직접 압박한다. 풀 크기는 ConfigMap(`DB_WRITER_POOL_SIZE` / `DB_READER_POOL_SIZE`)으로 조절한다.
- k8s에서는 `db-primary-service` / `db-reader-service`(ExternalName, 네임스페이스별)가 각각 Proxy writer 엔드포인트와 리플리카를 가리킨다. order/member/ai는 writer(Proxy) 하나만 쓴다.

> 배포 순서 주의: 라우팅 코드가 **먼저** 배포된 뒤에 리플리카를 붙여야 한다. 거꾸로 하면 catalog의 쓰기와 기동 시 Liquibase가 read-only 트랜잭션 에러로 죽는다.

### 4-5. AI · RAG

#### 인제스트 — 책 한 권을 검색 가능한 상태로

```mermaid
flowchart LR
    A[관리자 EPUB 등록<br/>catalog] -- afterCommit --> Q[[SQS ai-ingest]]
    Q --> W[SqsIngestListener<br/>1건씩]
    W --> S3[(S3 EPUB 원본)]
    S3 --> H{SHA-256<br/>이전과 같나?}
    H -- 같음 --> SKIP[건너뜀 · 재과금 방지]
    H -- 다름 --> P[본문 추출 → 900자 페이지 → 청크]
    P --> E[Titan v2 임베딩<br/>1024차원]
    E --> V[(S3 Vectors wiki-v1<br/>delete-then-put)]
    V --> C{적재 건수 대조}
    C -- 일치 --> WB[wiki_books 등록<br/>= 검색 가능]
    C -- 불일치 --> ERR[예외 → 재배달 → DLQ]
```

- **적재 순서**: 벡터 적재와 건수 검증이 끝난 **뒤에만** `wiki_books`에 등록한다. 반대로 하면 벡터가 반만 들어간 책이 검색 대상으로 노출된다.
- **멱등**: 벡터 키는 `{bookId}#{page}#{chunkSeq}`로 결정적이고, 적재 전에 그 책의 기존 벡터를 지운다.
- **ID 충돌 방어**: 같은 `bookId`에 제목이 다른 책이 오면 오류를 반환해 덮어쓰기를 막는다.
- 청크는 페이지 경계를 넘지 않는다 — 인용의 쪽수가 항상 하나로 정해진다.

#### 질의 — `POST /api/ai/lion/ask`

1. **쿼터 확인** (`DailyQuota`) — 무료 5회 / 구독 50회. 초과한 사용자에게 임베딩 비용을 태우지 않도록 **처리 전에** 본다.
2. **모드 결정** (`QueryRouter`) — "왜/어떻게/요약" 같은 설명 요구가 있으면 `answer`(LLM), 아니면 `search`(답변 생성 LLM 미호출, 임베딩·검색 비용은 발생).
3. **접근 제어** — 검색 허용 목록 = **구매한 책 ∪ (구독 중이면) 인제스트된 책 전체**. 클라이언트의 `bookIds`는 **교집합으로 좁히기만** 한다(합집합이면 남의 책 본문이 새어 나간다). 비면 검색 자체를 하지 않는다.
4. **임베딩 → 벡터 검색** — 허용 목록을 S3 Vectors `filter: {"bookId": {"$in": [...]}}`로 내려 **인덱스가 직접 거른다.** 전건 검색 후 자바에서 거르면 topK를 남의 책이 채워 결과가 조용히 나빠진다.
5. **개인 메모 제외** — `sourceType = user_summary` 벡터는 인용하지 않는다.
6. **거리 가드** — cosine distance가 `AI_MAX_DISTANCE`(기본 0.75)보다 먼 근거는 버린다. **근거가 없으면 LLM을 부르지 않고** "근거를 찾지 못했습니다"를 돌려준다. 근거 없는 답변 생성을 줄이기 위한 처리다.
7. **선별** — 같은 (책, 쪽)은 병합, 책당 최대 3개, 컨텍스트 예산 12KB.
8. **생성** — `answer` 모드에서만 Claude Haiku 4.5(temperature 0.2)가 `[1]`, `[2]` 인용 번호를 달아 답한다.
9. **사용량 기록** — 실제로 과금되는 일(임베딩·검색)을 했을 때만 쿼터를 1 올린다.

#### 장애를 다루는 방식이 기능마다 다르다

| 기능 | Redis/외부 장애 시 | 이유 |
| --- | --- | --- |
| 일일 쿼터 | **fail-open**(통과) + WARN | 과금 방어선이지 인증이 아니다. 공유 Redis 장애를 RAG 장애로 번지게 하지 않는다 |
| 접근 제어(구매 목록 캐시) | **fail-closed** — DB 원본으로 폴백 | 틀리면 안 되는 필터다 |
| 임베딩/벡터 검색 실패 | `grounded=false`로 degrade (503 아님) | AI 장애가 상담·주문으로 전이되면 안 된다 |
| 구독 조회 실패 | 비구독으로 강등(무료 상한, EXP 1배) | Feign fallback |

외부 API는 **Bulkhead를 셋으로 나눠** 격리한다 — `aiEmbedding`(20), `aiLlm`(10), 문의봇 `aiExternalApi`(20). LLM이 느려져도 `search` 모드(임베딩만)는 막히지 않는다. `@Bulkhead`는 AOP 프록시를 타야 하므로 `GuardedAiCalls`라는 얇은 층으로 분리했다(같은 클래스 안에서 부르면 격리가 조용히 사라진다).

기동 시 `VectorIndexVerifier`가 S3 Vectors 인덱스의 차원·거리척도·비필터 키를 설정과 대조하고, 다르면 **파드를 Ready로 만들지 않는다** — 어긋난 인덱스는 에러 없이 틀린 결과만 내기 때문이다.

#### 그 밖의 AI 기능

- **라이언 육성** — 완독한 책을 먹이면 EXP가 오른다(구독자 2배). 외부 API 호출 0회의 순수 게이미피케이션이고, `fed_books` PK로 중복 적립을 막는다.
- **의미 기반 추천** — 도서 변경이 커밋되면 Redis Stream으로 ai의 추천 인덱서(`recommendation-books-v1`)에 반영된다. catalog가 사용자의 행동 이력(찜·최근 본 책·구매 등)을 근거 문장으로 넘기면 ai가 그 인덱스에서 순위를 매기고, 결과 카드 큐는 Redis에 캐시한다.
- **문의봇 · 1:1 상담** — 문의봇이 FAQ만 근거로 1차 응답하고(`ai-bot`, Bulkhead `aiExternalApi`), 필요하면 상담사에게 연결한다. 상담 채팅 설계는 [4-7](#4-7-실시간-11-상담-멀티-파드-websocket).

### 4-6. 장애 격리와 Graceful Degradation

원칙은 두 줄이다. **조회는 degrade해서라도 응답한다. 돈이 걸린 판단은 확인 없이 통과시키지 않는다(fail-closed).**

| 장애 | 영향받는 기능 | 동작 | 이유 |
| --- | --- | --- | --- |
| order-service 다운 | 도서 목록·상세의 재고 | **HTTP 200**, `stockQuantity: -1`(조회 실패, 품절 `0`과 구분) | 어차피 구매가 불가능하니 재고 숫자만 잠시 안 보이면 된다 |
| order-service 다운 | 리뷰 작성 · eBook 열람 | **정상** — 구매 시점에 받은 권한으로 판단 | 리뷰가 결제 가용성에 종속되지 않게 |
| order-service 다운 | 관리자 입고 | 예외 → 재시도 안내 | 쓰기는 성공한 척하면 안 된다 |
| catalog-service 다운 | 장바구니 / 주문 | 장바구니는 "정보 조회 불가"로 표시, **주문은 거절** | 가격을 모르는 채로 `price=0` 주문이 승인되면 안 된다 |
| member-service 다운 | 가상카드 차감 | **결제 거절** | 한도 확인 없이 돈을 움직이지 않는다 |
| member-service 다운 | 구독권 결제 전 중복 검사 | **503** | 잘못 통과시키면 이중 결제가 돈이 나간 뒤에 드러난다 |
| member-service 다운 | AI 쿼터·EXP 배율의 구독 여부 | 비구독으로 강등 | 무료 상한 · EXP 1배로 안전하게 |
| ai-service 다운 | 추천 카드 | 규칙 기반(인기순) 추천으로 전환 | |
| Bedrock / S3 Vectors 장애 | RAG 질의 | `grounded=false` (503 아님) | AI 장애가 상담·주문으로 번지지 않게 |
| Redis 장애 | 일일 쿼터 / RAG 권한 캐시 / 상담 티켓 | 통과 / **DB 원본 폴백** / **거부** | 과금 방어선은 fail-open, 접근 제어·인증은 fail-closed |

Feign 호출은 Resilience4j 서킷 브레이커를 거쳐 위 fallback으로 떨어진다(`spring.cloud.openfeign.circuitbreaker.enabled` — 꺼져 있으면 `fallback` 속성이 조용히 무시된다). 첫 줄은 로컬에서 order 프로세스를 내려 직접 확인할 수 있다([실행 방법 5단계](local-development.md#5-동작-확인)).

### 4-7. 실시간 1:1 상담 (멀티 파드 WebSocket)

사용자와 상담사의 소켓이 **서로 다른 파드**에 붙을 수 있다는 전제에서 출발했다. 상태는 전부 Redis에 두고(방·전사 24h TTL, 방당 메시지 500개 · 1,000자 상한) DB에는 남기지 않는다.

- **JWT를 URL에 싣지 않는다.** 브라우저는 `new WebSocket()`에 `Authorization` 헤더를 붙일 수 없다. JWT를 쿼리로 보내면 액세스 로그·히스토리·`Referer`에 남으므로, 평범한 REST(`POST /api/ai/bot/chat/ticket`)로 신원을 확인한 뒤 **30초짜리 1회용 교환권**만 URL로 보낸다. 교환권은 `GETDEL`로 원자적으로 소비해 같은 링크로 두 소켓이 붙지 못한다.
- **핸드셰이크가 유일한 인증 지점이다.** 이후 프레임에 담긴 필드는 신원으로 쓰지 않는다. Redis 장애 시 티켓 검증은 fail-closed이고, 실패는 403이 아니라 **401**로 내려 프론트가 "티켓 재발급 후 재연결"과 "권한 없음"을 구분한다. 상담사 판정은 Cognito 그룹(`cognito:groups`)으로만 하고 티켓에 굳혀 넣는다.
- **Origin 검사는 서버가 한다.** WebSocket은 CORS preflight가 없어 Ingress의 CORS 설정이 적용되지 않는다(`CHAT_ALLOWED_ORIGINS`).
- **접속 중인 상담사는 Set이 아니라 하트비트 ZSET으로 센다.** OOMKill·노드 유실·노트북 덮개 닫기에서는 종료 콜백이 불리지 않아 Set에 유령이 남고, "상담사가 없으면 즉시 게시판 안내" 규칙이 조용히 무력화된다. 마지막 Pong 시각을 score로 두고 grace(60s, 서버 Ping 20s의 3배) 안의 범위만 센다.
- **배정은 Lua 스크립트 하나**로 상태 확인 · 전이 · 대기열 제거를 원자적으로 처리한다. 두 상담사가 동시에 눌러도 정확히 한 명만 이긴다.
- **팬아웃은 Pub/Sub**(컨슈머 그룹 아님). 소켓이 붙을 때 구독을 먼저, 전사 조회를 나중에 해 유실 없이 겹쳐 받게 하고 중복은 `seq`로 지운다. 방은 메시지 본문이 아니라 **채널명**으로 판별한다.

### 4-8. 보안 경계

| 경계 | 설계 |
| --- | --- |
| 서비스 간 전용 API | `/internal/**`은 Ingress에 **라우팅하지 않는다**. 추가로 NetworkPolicy가 order-service 인바운드를 catalog-service로만 좁힌다(kubelet probe는 Pod가 아니라 노드 IP에서 오므로 노드 대역을 따로 연다) |
| 인증 | Cognito가 발급한 JWT를 4개 서비스가 각자 Resource Server로 검증한다. 회원 식별(`sub`)과 닉네임은 토큰 클레임에서 읽어 **매 요청마다 member-service를 부르지 않는다** — 인증이 모든 요청의 임계 경로가 되지 않게 |
| eBook 원본 | 퍼블릭 액세스를 차단한 S3 버킷 + **10분짜리 Presigned URL**. 열람 권한 = 구매 이력 ∪ 구독 |
| 데이터 경계 | 스키마별 전용 계정 · 남의 스키마엔 GRANT 없음 · `public` 스키마 CREATE 회수 |
| AWS 자격증명 | CI는 GitHub OIDC로 역할을 받는다(장기 액세스 키 없음). 파드는 서비스별 **IRSA**로 필요한 권한만(SQS 발행/소비, 미디어 버킷, Bedrock, S3 Vectors) |
| 엣지 | CloudFront + WAF(IP당 rate limit, AWS 관리형 공통 규칙). 오리진 NLB가 인터넷에 열려 WAF를 우회할 수 있던 것을 부하 테스트 중 발견해, **CloudFront origin-facing prefix list만** 허용하도록 막았다 |
| 시크릿 | gitleaks로 PR·push마다 전체 히스토리 스캔, 값은 GitHub Environments / Secrets Manager / SSM에만 |

---

## 인프라 · 비용 설계

작은 팀 예산으로 운영 환경 두 벌을 돌리기 위한 결정들이다.

- **클러스터 하나에 dev/prod 두 네임스페이스(integrated).** 대신 서로를 굶기지 못하게 막았다.
  - 네임스페이스별 **ResourceQuota** — 불변식 `quota ≥ Σ(maxReplicas × 파드 요청량) + 롤링 서지 여유`. 이게 깨지면 HPA가 천장에 닿는 순간 롤링 업데이트의 새 파드가 admission에서 거절돼 배포가 멈춘다([트러블슈팅](troubleshooting-deploy-scaling.md#4-resourcequota가-꽉-차서-롤링-업데이트가-새-파드를-하나도-못-만들었다)).
  - **PriorityClass** prod 1000 / dev 100 — 노드가 모자라면 dev가 먼저 밀려난다.
  - HPA 상한을 환경별로 분리(catalog 6/10, order 8/30), DB ExternalName Service도 네임스페이스별로 둬 dev가 prod DB를 가리킬 수 없게 했다.
- **Karpenter**: 스팟 + 온디맨드 혼합, `WhenEmptyOrUnderutilized` 30초 consolidation, 스팟 회수 통지를 EventBridge → SQS(interruption queue)로 받아 회수 전에 노드를 비운다.
- **DB 한 대**: RDS PostgreSQL 인스턴스 하나에 스키마 4개(격리는 계정 권한), 벡터는 S3 Vectors로 빼서 pgvector용 클러스터가 따로 필요 없다. 쓰기는 RDS Proxy가 멀티플렉싱하고 읽기는 리플리카가 받는다.
- **AI 비용 방어선**: 일일 쿼터(과금되는 일을 한 질의만 차감) · `search` 모드는 LLM 0회 · 근거가 없으면 LLM 미호출 · 같은 EPUB은 SHA-256 비교로 재임베딩 생략 · 인제스트는 1건씩(스로틀링 재시도로 과금이 불어나지 않게) · SQS 리스너는 기본 꺼짐(로컬에서 모르게 과금되지 않게).
- **Terraform 계층 분리**: `00-base`(DNS·인증서·WAF·S3·ECR·GitHub OIDC) → `01-data`(RDS·RDS Proxy·Valkey·SQS·Cognito) → `02-runtime`(EKS·Karpenter·ALB·엣지 라우팅·서비스별 IRSA). state가 나뉘어 있어 데이터 계층을 두고 런타임만 부수고 다시 만들 수 있다. AWS가 만드는 값은 SSM Parameter Store 하나를 원본으로 CD가 읽는다.
- **알림**: RDS 커넥션·CPU·메모리, Valkey 메모리, Pod CPU CloudWatch 알람이 SNS 토픽 하나로 모여 이메일로 온다.
