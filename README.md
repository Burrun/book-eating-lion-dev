# Book Eating Lion (책 먹는 사자) — MSA 전환본

K8s 및 AWS EKS 기반 금융/결제 연동 도서 쇼핑몰 시스템

> **이 디렉터리는 원래 있던 모듈러 모놀리스를 4개 마이크로서비스로 전환한 결과물이다**
> (전환을 실행한 `docs/msa-migration-plan.md`는 커밋된 적이 없어 저장소에 없다 — 그 계획의
> 결과와 근거는 아래 [MSA 전환 요약](#-msa-전환-요약)과 [핵심 설계 결정 4가지](#-핵심-설계-결정-4가지)에
> 정리돼 있으니 이 문서가 사실상의 대체본이다). 전환 전 코드는 상위 디렉터리에 그대로 있다.

---

## 🧭 MSA 전환 요약

### 서비스 구성 — 기능으로 3개, 자원 프로파일로 1개 더

| 서비스 | 포함 도메인 | 포트 | DB | HPA |
| --- | --- | --- | --- | --- |
| `catalog-service` | book, review, wishlist, recent-books | 8081 | `catalog_db` | 2 → 20 |
| `order-service` | order, payment, delivery, **inventory** | 8082 | `order_db` | 2 → 30 |
| `member-service` | member, auth, address | 8083 | `member_db` | 2 → 6 |
| `ai-service` | 먹인 책 RAG, 라이언, 고객상담(문의봇·faq) | 8084 | `ai_db` + S3 Vectors | rag 1→6 / bot 1→10 |

**서비스 4개 / Deployment 5개**다. `ai-service` 는 이미지 1개에 Deployment 2개(`ai-rag`,
`ai-bot`)로 뜬다 — 두 워크로드의 HPA 메트릭과 타임아웃 정책이 다르기 때문이다.
자원 격리는 YAML 하나로 얻을 수 있고, 서비스를 쪼개는 건 그보다 10배 비싸다.

`member-service` 는 의도적으로 작고 지루하다. 인증이 CPU 를 두고 RAG 와 경쟁하면
부하 시험 중 로그인이 죽고, 그러면 "결제 성공률 100%" 시연 자체가 무의미해진다.

### 바뀐 것 — 한눈에

| 영역 | 전환 전 | 전환 후 |
| --- | --- | --- |
| 배포 단위 | `apps/api` 1개 | `apps/{catalog,order,member,ai}-api` 4개 |
| 도메인 모듈 | common, member, book, order, usedbook, delivery | common, member, book, order, **ai** (usedbook 삭제, delivery 분해) |
| DB 엔진 | MySQL 8.0 | **PostgreSQL 16** (클러스터 1개, 스키마 4개) |
| 벡터 저장소 | `lion_memories.embedding` (JSON 칼럼) | **S3 Vectors** (`wiki-v1` 인덱스) |
| DB 경계 | 단일 스키마·단일 계정 | **스키마 4분할 + 서비스별 계정 권한** |
| 재고 소유권 | `books.stock` (catalog) | **`order_db.inventory` (order)** |
| k8s | Deployment 1 / Service 1 / HPA 1 | **Deployment 5 / Service 5 / HPA 5** |
| CD | 전체 재배포 | **변경된 서비스만** (paths filter + matrix) |
| 계약 | 산문 `.md` | **OpenAPI YAML 4종** |

---

## 🔑 핵심 설계 결정 4가지

### ① 재고는 order-service 가 소유한다

`books.stock` 을 `order_db.inventory` 로 옮겼다. 이 한 줄이 **Saga 와 보상 트랜잭션을
통째로 없앤다** — Redlock·재고차감·결제가 전부 한 서비스 안의 로컬 트랜잭션이 되기 때문이다.

라이프사이클만 보면 재고는 상품에 가깝지만, 애그리게잇의 결정 기준은 라이프사이클이
아니라 **트랜잭션 경계**다. KPI 가 "오버셀링 0건"이면 재고의 일관성 경계는 주문 쪽이다.

대가로 두 가지를 설계해야 했다:

- **관리자 입고** → catalog 는 `order_db` 쓰기 권한이 없으므로 `POST /internal/inventory/{bookId}/restock` 으로 위임
- **재고 표시** → `GET /internal/inventory?bookIds=` **벌크 조회**. 단건 API 를 주면 목록에서 반드시 N+1 이 난다

### ② 서비스 간 통신은 딱 세 채널뿐이다

기준은 **"장애가 전이돼도 되는가"**다.

| 채널 | 용도 | 판단 근거 |
| --- | --- | --- |
| **동기** (OpenFeign + Resilience4j) | `catalog → order` 재고 조회·입고 **2건뿐** | 지금 이 응답에 값이 필요하다. fallback 이 있어 장애 반경이 좁다 |
| **비동기** (Redis Streams) | 리뷰 권한 발급, 닉네임 스냅샷 | 동기로 하면 **리뷰 작성이 결제 가용성에 종속**된다 |
| **통신 없음** | 재고 차감, 구매 검증, 도서 정보 스냅샷 | 이쪽이 가장 중요하다 |

리뷰 작성 시 "구매했나?"를 order 에 **묻지 않는다.** 구매 확정 시점에 order 가
`ReviewPermissionGranted` 이벤트로 권한을 미리 넘긴다. 그래서 **order-service 가 죽어도
리뷰 작성은 정상 동작한다.** 동기 호출로 바꾸면 "장애가 결제로 전이되지 않는다"는
프로젝트 명분과 정확히 반대가 된다.

### ③ 격리는 클러스터가 아니라 계정 권한이 만든다

PostgreSQL **클러스터 1개 / 스키마 4개 / 계정 4개**다.

원래는 `ai_db` 만 별도 클러스터(Serverless v2)로 뺐었다. 근거가 둘이었는데 둘 다 없어졌다 —
**① pgvector 가 필요하다** → 벡터를 전부 S3 Vectors 로 옮겨서 불필요.
**② auto-pause 로 과금 독립** → ①이 사라지자 클러스터를 하나 더 띄울 값을 못 한다.

경계를 만드는 건 계정이다. `catalog_svc` 로 `order_db` 를 조회하면 여전히
`permission denied for schema order_db` 다. 클러스터를 가르는 건 그 위에 얹는
비용 문제였을 뿐이고, 그 비용을 낼 이유가 사라졌다.

포팅 비용은 실측상 거의 전부 기계적 치환이었다(`nativeQuery` 0건, `@Query` 는 JPQL 2건,
`updated_at` 은 `@LastModifiedDate` 가 이미 관리 → **트리거 0개 필요**).

### ④ CPU 기반 HPA 는 I/O 바운드 워크로드에 무력하다

문의봇은 외부 LLM API 를 호출하므로 요청 100건이 동시에 들어와도 CPU 는 5% 언저리다.
CPU 70% HPA 는 **영원히 트리거되지 않고** 요청만 큐에 쌓이다 타임아웃된다.
그래서 `ai-bot` 은 동시 요청 수로 확장하고, 두 워크로드 모두 **Bulkhead 스레드풀 격리**를
필수로 적용했다.

---

## 📁 폴더 구조

```text
copy/
├── .github/
│   ├── CODEOWNERS                  # 🆕 도메인별 리뷰 소유권 (경계 강제 2단)
│   └── workflows/
│       ├── backend-ci.yml          # 🔄 모듈 경계 검사 + 이미지 4종 빌드 검증
│       ├── frontend-ci.yml
│       ├── main-cd.yml             # 🔄 matrix + paths filter (변경된 서비스만 배포)
│       └── secret-scan.yml
│
├── backend/
│   ├── contracts/                  # 🆕 Phase 0-4 산출물 = 단일 진실 공급원
│   │   ├── catalog-v1.yaml
│   │   ├── order-v1.yaml
│   │   ├── member-v1.yaml
│   │   └── ai-v1.yaml
│   │
│   ├── apps/                       # 배포 단위 = bootJar = 컨테이너 이미지
│   │   ├── catalog-api/            # 🆕 Feign 클라이언트 + Fallback + 이벤트 소비 배선
│   │   ├── order-api/              # 🆕
│   │   ├── member-api/             # 🆕
│   │   └── ai-api/                 # 🆕
│   │
│   └── modules/                    # 도메인 로직 (라이브러리 jar). 서로 의존 금지
│       ├── common/                 # BaseEntity, 예외, 응답, RedisConfig, 이벤트 계약
│       ├── book/                   # + port/InventoryPort, ReviewPermission
│       ├── order/                  # + inventory/, delivery/ (병합)
│       ├── member/                 # + address/ (delivery 에서 이동)
│       └── ai/                     # 🆕 wiki/(먹인 책 RAG), lion/, bot/(고객상담)
│                                   # ❌ usedbook 삭제
│
├── k8s/
│   ├── base/                       # namespace, secret, db, ingress, networkpolicy
│   ├── catalog/                    # deployment, service, hpa, configmap
│   ├── order/
│   ├── member/
│   └── ai/                         # deployment-rag + deployment-bot (이미지 1개)
│
├── db/postgres/                    # 🔄 클러스터 1개 (구 cluster-a + cluster-b)
│   ├── 00-init.sql                 #    스키마 4개 + 서비스 계정 4개 + 권한
│   ├── 01~04-*.sql                 #    목표 스키마 (04 = ai_db)
│   └── 90-demo-data.sql            #    로컬 데모 데이터
│
├── frontend/                       # 🔄 찜/최근본상품 API 경로 변경 반영
├── k6/  ·  docs/  ·  nginx/
├── docker-compose.yml              # 🔄 postgres 1대 + redis + 서비스 4개
└── docker-compose-aws.yml          # 🔄 서비스별 계정/엔드포인트 분리
```

---

## ⚠️ 프론트엔드 영향 — API 경로 2개가 바뀌었다

계획서에 없던 항목이지만 서비스 분리의 직접적 결과다.

| 이전 | 이후 | 이유 |
| --- | --- | --- |
| `GET /api/members/me/wishlist` | `GET /api/catalog/wishlist/me` | `/api/members/**` 는 member-service 로 라우팅되는데, 찜 목록은 **catalog_db 소유 데이터**다 |
| `GET /api/members/me/recent-books` | `GET /api/catalog/recent-books/me` | 동일 |

같은 접두사를 두 서비스가 나눠 가지면 라우팅이 경로 길이에 의존하게 되고, 규칙 하나만
잘못 건드려도 요청이 엉뚱한 서비스로 간다. `frontend/src/api/wishlist.ts` 는 수정 완료했다.

---

## 🐳 로컬 실행

```bash
# 전체 기동 (postgres 1대 + redis + 서비스 4개 + nginx)
docker compose up --build -d

# 초기화 재기동 (스키마/데모데이터를 다시 넣으려면 -v 필수)
docker compose down -v && docker compose up --build -d
```

기동 확인:

```bash
curl http://localhost:8081/actuator/health   # catalog
curl http://localhost:8082/actuator/health   # order
curl http://localhost:8083/actuator/health   # member
curl http://localhost:8084/actuator/health   # ai
```

### ⚡ 계약 YAML → 프론트엔드 타입 생성

`frontend/` 에서 실행한다:

```bash
pnpm dlx openapi-typescript "../backend/contracts/*.yaml" -o src/api/types.ts
```

```ts
import type { components } from "./types";

type AskResult = components["schemas"]["AskResult"];
type Citation  = components["schemas"]["Citation"];
```

**실서버가 아니라 `backend/contracts/*.yaml` 에서 뽑는다.** 백엔드를 안 띄워도 되고,
계약이 곧 타입이라 구현이 계약을 벗어나면 프론트에서 타입 에러로 드러난다.

---

## ✅ 검증된 항목

로컬에서 실제로 확인한 것들이다.

| 항목 | 방법 | 결과 |
| --- | --- | --- |
| 4개 앱 독립 빌드 (Phase 2-1) | `./gradlew :apps:*-api:bootJar` | ✅ 4개 jar 생성 |
| 전체 테스트 | `./gradlew test` | ✅ 통과 |
| 스키마 4분할 (초기 검증 시점 스냅샷) | `pg_tables` 조회 | ✅ 9개 테이블이 3개 스키마에 정확히 귀속 |
| 스키마 4분할 (현재 기준) | `db/postgres/*.sql` 대조 | ✅ 34개 테이블이 `member_db`/`catalog_db`/`order_db`/`ai_db` 4개 스키마에 귀속 — 전체 목록·컬럼은 `docs/개발 문서/db-erd-v2.md` 참고 |
| **스키마 경계 강제** (Phase 1.5) | `catalog_svc` 로 `order_db.inventory` 조회 | ✅ `permission denied for schema order_db` |
| 재고 소유권 이전 (Phase 0-1) | `catalog_db.books` 에 stock 컬럼 확인 | ✅ 0건 (order_db.inventory 로 이동) |
| 계약 YAML 파싱 + `$ref` 무결성 | `yaml.safe_load` 4종 | ✅ 통과 (`ai-v1.yaml` 파싱 오류 2건 수정 후) |
| 계약 YAML 파싱 CI 자동화 | `backend-ci.yml` "계약 YAML 파싱" 스텝 확인 | ✅ 파일 0건이면 실패하도록 구성됨. 단 **YAML 문법만 검사** — 구현이 계약과 실제로 일치하는지(엔드포인트 존재 여부 등)는 아직 검사 안 함 |
| 자료 → JSONL 변환 | `python scripts/build-corpus-jsonl.py` | ✅ 5편 / 46페이지 / 46청크, 불변식 3종 통과 |
| **Redlock 재고차감 + 취소·환불** | `InventoryLockExecutor`(Redisson, bookId 정렬 락) 실사용처 확인, `OrderService.cancelOrder`/`requestReturn`/`refundOrder` 코드 확인 | ✅ 상태 검증(`OrderCannotBeCancelledException` 등)까지 구현 완료. **교환(Exchange)만 별도 미구현** — DB엔 상태값만 있고 코드 없음 |
| **먹인 책 RAG** (`/api/ai/lion/ask`) | `AskController` + `WikiRagService`(215줄, 구매도서 필터링 포함) 코드 확인 | ✅ 일일 쿼터·요청 검증까지 갖춘 완성된 구현 |
| **Bedrock 실연동** | `BedrockEmbeddingClient`/`BedrockLlmClient`/`S3VectorSearchAdapter` 파일 확인 | ✅ 존재 확인 |
| 4개 서비스 기동 | `/actuator/health` | ✅ 전부 UP |
| 재고 API 조합 | `GET /api/catalog/books/1` | ✅ `stockQuantity=100` (order 에서 조합) |
| **Fallback** (Phase 2-3) | order 강제 종료 후 도서 상세 조회 | ✅ **HTTP 200**, 도서 정보 정상, `stockQuantity=-1` 로 degrade |

마지막 항목이 이 전환의 핵심 증거다 — **결제 서비스가 죽어도 서점은 계속 돈다.**

### 재현 방법

```bash
docker compose stop order
curl http://localhost:8081/api/catalog/books/1     # HTTP 200, stockQuantity: -1
docker compose start order
curl http://localhost:8081/api/catalog/books/1     # stockQuantity: 100
```

> `stockQuantity: -1` 은 "재고 조회 실패"를 뜻한다. 품절(`0`)과 구분해야 하므로
> 음수를 쓴다. 프론트는 이 값에서 재고 영역만 degrade 하고 도서 정보는 정상 노출한다.

---

## 🚧 남은 작업 (의도적 미완)

전환 범위는 **구조 전환**까지다. 아래는 Phase 1(팀별 병렬 기능 개발) 영역이다.

> ⚠️ 이 표는 한동안 갱신이 안 돼 있었습니다. 아래는 코드를 직접 대조해서 다시 정리한 것입니다 — 실제로는 절반 가까이 이미 완료 상태였습니다(완료 항목은 위 "검증된 항목"으로 옮김).

| 항목 | 현재 상태 |
| --- | --- |
| 주문 교환(Exchange) 로직 | 취소·환불·Redlock 재고차감은 완료(위 "검증된 항목" 참고). `orders.order_status`엔 `EXCHANGE_REQUESTED`/`EXCHANGED` 상태값이 있지만, 코드 어디에도 교환 처리 메서드가 없다 — 상태값만 선언돼 있고 실제로 못 씀 |
| Contract test(구현-계약 일치 검사)를 `backend-ci.yml` 에 추가 | YAML 파싱 검사는 이미 추가됨(위 "검증된 항목" 참고). 하지만 "실제 구현이 계약과 일치하는가"(엔드포인트 실존 여부, 요청/응답 스키마 등)는 여전히 자동 검사 안 됨 — 문서에만 존재하는 엔드포인트가 계속 쌓일 수 있는 상태 |
| Flyway 활성화 | 엔티티와 `db/postgres/01~04` 목표 스키마가 아직 정렬되지 않아 `enabled: false`. 전환 전에도 같은 이유로 꺼져 있었다 |
| 리뷰 권한 이벤트 유실 대비 fallback 동기 조회 | 미구현. 이벤트가 유실되면 실제 구매자도 리뷰를 못 쓴다 |
| `ai-bot` 동시요청 HPA | 매니페스트는 작성했으나 **Prometheus Adapter / KEDA 미설치 시 동작하지 않는다** (`k8s/`에 관련 매니페스트 없음 확인) |
| `gradlew build` | spotless `ratchetFrom origin/main` 때문에 실패한다. `ai-api` 앱 전체가 아직 `main` 에 없어 전부 검사 대상이 된다. `gradlew test` 는 통과 |

---

## ⚙️ 환경 및 검증 경계 관리

- **로컬 독립 검증 (`docker-compose.yml`)**:
  - `SPRING_PROFILES_ACTIVE=local` 적용.
  - 로컬 Postgres/Redis 기반의 빠른 개발 및 DB 계정 권한 격리(`permission denied`) 테스트 전용.
  - ⚠️ **테스트 제약**: SQS 인제스트 파이프라인, S3 Vectors, Bedrock LLM 및 ElastiCache 클러스터 연동은 로컬 Docker에서 동작하지 않으므로 AWS 연동 환경에서 검증함.
- **AWS 직접 연동 검증 (`docker-compose-aws.yml`)**:
  - `SPRING_PROFILES_ACTIVE=prod` 적용.
  - AWS Aurora, ElastiCache, SQS, Bedrock, S3 Vectors 실제 인프라와 직결하여 EKS 배포 호환성을 100% 검증함.
- **권한 분리 원칙**:
  - 모든 환경에서 서비스별 전용 DB 계정(`catalog_svc`, `order_svc`, `member_svc`, `ai_svc`)을 사용하여 스키마 경계를 강제함.
  - 명시적 환경변수(`AWS_ACCESS_KEY_ID` 등)로 자격증명을 전달함.

---

## 📚 참고

- 전환 계획서(`docs/msa-migration-plan.md`)는 저장소에 없음 — 위 [핵심 설계 결정 4가지](#-핵심-설계-결정-4가지)가 그 요약을 대신함
- 계약 문서: `backend/contracts/README.md`
- 그 외 기획/DB/이벤트 문서 전체 지도: `docs/개발 문서/README.md`
