# Book Eating Lion (책 먹는 사자)

K8s 및 AWS EKS 기반 금융/결제 연동 도서 쇼핑몰 MSA 시스템

---

## 🧭 서비스 아키텍처 및 구성

| 서비스 | 담당 도메인 | 포트 | DB | HPA 스케일 범위 |
| --- | --- | --- | --- | --- |
| `catalog-service` | Book, Review, Wishlist, Recent-books | 8081 | `catalog_db` | 2 → 20 |
| `order-service` | Order, Payment, Delivery, Inventory | 8082 | `order_db` | 2 → 30 |
| `member-service` | Member, Auth, Address | 8083 | `member_db` | 2 → 6 |
| `ai-service` | RAG(도서 질의응답), Lion(사자 육성), Customer Chat | 8084 | `ai_db` + S3 Vectors | RAG 1→6 / Bot 1→10 |

> **Deployment 구성**: `ai-service`는 단일 애플리케이션 이미지 기반이지만 HPA 메트릭 및 워크로드 격리를 위해 2개 Deployment(`ai-rag`, `ai-bot`)로 분리 배포됩니다.

---

## 🔑 주요 아키텍처 특징

- **재고 소유권 (`order-service`)**: 오버셀링 방지 및 트랜잭션 경계 확립을 위해 재고 관리(`inventory`)를 `order-service`가 소유합니다.
- **서비스 간 동기 통신 및 Fallback (OpenFeign)**:
  - `catalog → order` (`OrderInventoryClient`): 재고 벌크 조회 및 입고 (`OrderInventoryFallback` 적용으로 결제/주문 서비스 장애 시에도 도서 조회가 5xx 없이 Degrade 작동).
  - `order → catalog` (`CatalogClient`): 주문/장바구니 시 클라이언트 조작 방지를 위한 도서 정보 및 가격 실시간 검증 (`CatalogClientFallback`).
  - `order → member` (`CardClient`): 결제 승인/취소 시 가상카드 한도 동기 차감 및 복구 처리 (`CardClientFallback`).
- **서비스 간 비동기 메시징 (Redis Streams / SQS)**:
  - 리뷰 작성 권한 이벤트, 도서 EPUB 인제스트 및 결제 완료 구매 이벤트를 비동기로 처리하여 핵심 결제 가용성을 보장합니다.
- **PostgreSQL 스키마 및 계정 격리**: 단일 PostgreSQL 클러스터 내 4개 독립 스키마(`catalog_db`, `order_db`, `member_db`, `ai_db`)와 서비스별 전용 계정(`catalog_svc`, `order_svc` 등)을 사용하여 DB 통신 경계를 강제합니다.
- **I/O 바운드 확장 (KEDA/Custom HPA)**: LLM/AI 요청 특성에 맞춰 동시 요청 수 기반으로 HPA 확장을 적용합니다.

---

## 📁 디렉터리 구조

```text
.
├── .github/workflows/          # CI/CD 파이프라인 (backend-ci, frontend-ci, main-cd)
├── backend/
│   ├── contracts/              # 서비스 간 OpenAPI 계약 문서 (YAML)
│   ├── apps/                   # 마이크로서비스 배포 단위 (bootJar)
│   │   ├── catalog-api/
│   │   ├── order-api/
│   │   ├── member-api/
│   │   └── ai-api/
│   └── modules/                # 도메인 코어 모듈 (common, book, order, member, ai)
├── k8s/                        # Kubernetes 매니페스트 (base 및 서비스별 deployment)
├── db/postgres/                # PostgreSQL 초기화 SQL 및 스키마 분할 정의
├── frontend/                   # React + TypeScript 프론트엔드
├── docker-compose.yml          # 로컬 개발용 인프라 스택
└── docker-compose-aws.yml      # AWS 실제 연동 검증용 인프라 스택
```

---

## 🔌 API 경로 라우팅 및 계약

외부 API 호출 시 백엔드 서비스로 전달되는 4개 주요 접두사 라우팅 규격입니다:

| 외부 API 경로 접두사 | 담당 서비스 | 로컬 포트 |
| --- | --- | --- |
| `/api/catalog/**` | catalog-service | 8081 |
| `/api/orders/**`, `/api/cart/**`, `/api/coupons/**`, `/api/payments/**` | order-service | 8082 |
| `/api/members/**`, `/api/auth/**`, `/api/cards/**` | member-service | 8083 |
| `/api/ai/**`, `/ws/ai/**` | ai-service | 8084 |

### OpenAPI 계약 ➔ TypeScript 타입 생성

`frontend/` 디렉터리에서 아래 명령어로 계약 파일 기반 프론트엔드 타입을 생성합니다:

```bash
cd frontend
npx openapi-typescript ../backend/contracts/catalog-v1.yaml -o src/api/generated/catalog.ts
npx openapi-typescript ../backend/contracts/order-v1.yaml   -o src/api/generated/order.ts
npx openapi-typescript ../backend/contracts/member-v1.yaml  -o src/api/generated/member.ts
npx openapi-typescript ../backend/contracts/ai-v1.yaml      -o src/api/generated/ai.ts
```

> glob 패턴 금지, npm 고정, `-o` 대상 주의 등 상세는 `docs/frontend/type-generation.md` 참고.

---

## 🐳 실행 가이드

### 1. 로컬 환경 실행 (`SPRING_PROFILES_ACTIVE=local`)

Postgres 1대 + Redis + 4개 Microservice + Nginx 전용 스택 실행:

```bash
# 로컬 스택 기동
docker compose up --build -d

# 헬스체크 확인
curl http://localhost:8081/actuator/health   # catalog
curl http://localhost:8082/actuator/health   # order
curl http://localhost:8083/actuator/health   # member
curl http://localhost:8084/actuator/health   # ai
```

### 2. AWS 서비스 연동 실행 (`SPRING_PROFILES_ACTIVE=prod`)

AWS Bedrock, S3 Vectors, SQS, Cognito 연동 검증 시 실행:

```bash
docker compose -f docker-compose-aws.yml up --build -d
```

---

## ⚙️ 환경 및 검증 경계 관리

- **로컬 독립 검증 (`docker-compose.yml`)**: 로컬 Postgres/Redis 기반 빠른 개발 및 DB 계정 권한 격리(`permission denied`) 검증.
- **AWS 연동 검증 (`docker-compose-aws.yml`)**: AWS SQS, Bedrock, S3 Vectors, Cognito 등 실제 Cloud API와의 연동 검증.
- **권한 분리 원칙**: 모든 환경에서 서비스별 전용 DB 계정(`catalog_svc`, `order_svc`, `member_svc`, `ai_svc`)을 사용하여 데이터 접근 권한을 엄격히 제한.
