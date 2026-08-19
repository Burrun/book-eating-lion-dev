# k8s 명세

## 1. 클러스터 및 아키텍처 개요

본 명세서는 Kubernetes 클러스터 내 마이크로서비스 배포, 네트워크 라우팅, 오토스케일링 정책 및 보안 구성을 정의합니다.

### 1.1 네임스페이스 구성

* `lion-app`: 백엔드 마이크로서비스(4개), Ingress, NetworkPolicy가 배포되는 메인 애플리케이션 영역입니다.
* `lion-db`: Aurora PostgreSQL ExternalName 서비스가 배치되는 데이터베이스 전용 영역으로, `access-to-db: allowed` 레이블이 부여됩니다.

### 1.2 데이터베이스 아키텍처

* **PostgreSQL 단일 Amazon Aurora 클러스터**: `catalog_db`, `order_db`, `member_db`, `ai_db` 4개 스키마로 분할 관리합니다.
* **권한 격리**: 클러스터 분리 대신 서비스별 DB 계정 권한을 독립적으로 부여하여 격리를 구현합니다.

### 1.3 외부 진입점 및 프런트엔드

* **Ingress Controller**: `ingress-nginx` 기반 단일 Ingress(`lion-ingress`)로 모든 외부 API 요청을 수신합니다. 별도의 API Gateway(Spring Cloud Gateway)는 도입하지 않아 단일 장애점(SPOF)을 최소화합니다.
* **프런트엔드 서빙**: Amazon S3 및 CloudFront를 통해 별도 서빙되며, Kubernetes Ingress는 `/api/*` 경로 요청 처리만을 담당합니다.

### 1.4 서비스 디스커버리 및 AI 워크로드 분리

* **Service DNS**: Eureka 등의 외부 디스커버리 솔루션 없이 Kubernetes Service DNS(`*.lion-app.svc.cluster.local`, `*.lion-db.svc.cluster.local`)를 사용합니다.
* **AI 워크로드 격리**: 동일 컨테이너 이미지를 공유하되, 요청 경로에 따라 `ai-rag`(질의/RAG)와 `ai-bot`(문의봇)으로 Deployment, Service, HPA, 리소스 프로파일을 물리적으로 분리 배포합니다.

## 2. 서비스별 자원 및 프로브 명세

### 2.1 컨테이너 자원 정책 (QoS & Resource)

| **서비스** | **Requests (CPU/MEM)** | **Limits (CPU/MEM)** | **QoS Class** | **설정 근거 및 특이사항** |
| -- | -- | -- | -- | -- |
| **catalog** | 250m / 512Mi | 1000m / 1Gi | **Burstable** | 읽기 요청 95% 비율의 캐시 친화적 워크로드 |
| **member** | 200m / 384Mi | 500m / 768Mi | **Burstable** | 경량 서비스(BCrypt 및 JWT 검증)로 자원 소모 최소 |
| **order** | 500m / 1Gi | 500m / 1Gi | **Guaranteed** | Requests와 Limits를 동일하게 강제하여 결제/재고 트랜잭션 중 스로틀링 및 축소(Eviction) 방지 |
| **ai-rag** | 500m / 512Mi | 2000m / 2Gi | **Burstable** | 소수 사용자의 간헐적 버스트 패턴 처리를 위한 리소스 상하한 격차 설정 |
| **ai-bot** | 100m / 384Mi | 500m / 768Mi | **Burstable** | 외부 LLM API 응답 대기 위주 워크로드로 CPU 요구량 최소화 |

### 2.2 헬스체크 및 배포 전략 (Probes & Strategy)

| **서비스** | **Startup Probe** | **Readiness / Liveness Probe** | **Termination Grace Period** | **배포 전략** |
| -- | -- | -- | -- | -- |
| **catalog** | `/actuator/health/readiness`*(5s 간격, threshold 36 / 약 3분)* | `/actuator/health/readiness``/actuator/health/liveness`*(10s 간격, threshold 3, http=8080)* | **30s** | RollingUpdate*(maxSurge 1 / maxUnavailable 0)* |
| **member** | `/actuator/health/readiness`*(5s 간격, threshold 12 / 약 1분)* | 동일 | **30s** | RollingUpdate |
| **order** | `/actuator/health/readiness`*(5s 간격, threshold 36 / 약 3분)* | 동일 | **30s** | RollingUpdate |
| **ai-rag** | `/actuator/health/readiness`*(5s 간격, threshold 36 / 약 3분)* | 동일 | **30s** | RollingUpdate |
| **ai-bot** | `/actuator/health/readiness`*(5s 간격, threshold 36 / 약 3분)* | 동일 | **30s** | RollingUpdate |

> 💡 **운영 세부사항**
>
> * `member` 서비스의 Startup Probe 상한이 짧은 이유는 무거운 초기화 작업이 존재하지 않기 때문입니다.
> * 모든 Deployment 매니페스트 내 `replicas` 필드는 명시하지 않으며, HPA가 파드 수 제어 권한을 소유합니다.
> * **Pod Anti-Affinity** (`preferredDuringSchedulingIgnoredDuringExecution`, weight: 100, topologyKey: `kubernetes.io/hostname`)는 `catalog`, `member`, `order`, `ai-rag`, `ai-bot` 5개 서비스 전체에 적용됩니다.

## 3. Ingress L7 라우팅 명세

* **Ingress 설정**: `lion-ingress` (lion-app 네임스페이스), ingressClassName: nginx, Host `${API_HOST}`
* **CORS 설정**: enable-cors=true, allow-origin=`${FRONTEND_ORIGIN}`, allow-methods=GET,POST,PUT,PATCH,DELETE,OPTIONS, **allow-headers=Authorization,Content-Type,X-Member-Id**, allow-credentials=false
* **라우팅 규칙**: 매니페스트 상단 명시 순서에 따라 매칭됩니다.

| **Path (Prefix)** | **Backend Service** | **Port** | **비고 및 설계 의도** |
| -- | -- | -- | -- |
| `/api/ai/lion` | **ai-rag** | 8080 | RAG 질의 처리 |
| `/api/ai/ask` | **ai-rag** | 8080 | **AI RAG 추가 질의 처리** |
| `/api/ai/bot` | **ai-bot** | 8080 | 문의봇 처리 (봇 요청 폭주 시 RAG 장애 전이 차단) |
| `/api/orders` | **order-service** | 8080 | 주문 관련 처리 |
| `/api/cart` | **order-service** | 8080 | 장바구니 처리 |
| `/api/coupons` | **order-service** | 8080 | 쿠폰 처리 |
| `/api/payments` | **order-service** | 8080 | 결제 처리 |
| `/api/members` | **member-service** | 8080 | 회원 정보 처리 |
| `/api/auth` | **member-service** | 8080 | 인증 처리 |
| `/api/cards` | **member-service** | 8080 | 카드 정보 처리 |
| `/api/books` | **catalog-service** | 8080 | 도서 정보 처리 ⚠️ |
| `/api/reviews` | **catalog-service** | 8080 | 리뷰 처리 ⚠️ |
| `/api/wishlist` | **catalog-service** | 8080 | 위시리스트 처리 ⚠️ |
| `/api/recent-books` | **catalog-service** | 8080 | 최근 본 도서 처리 ⚠️ |

> 🔒 **내부 전용 경로 제약**: `/internal/**` 경로는 외부 노출 차단을 위해 Ingress 라우팅 규칙에서 제외합니다.
>
> ⚠️ **\[알려진 버그\] catalog 4개 경로가 실제 컨트롤러와 안 맞습니다.** 위 표는 `k8s/base/08-ingress.yaml`에 실제로 적힌 그대로입니다 — 하지만 catalog 쪽 컨트롤러는 전부 `/api/catalog/*` 밑에 있습니다(`BookController` → `/api/catalog/books`, `ReviewController` → `/api/catalog/books/{bookId}/reviews`, `WishlistController`/`MemberBookQueryController` → `/api/catalog/wishlist`, `/api/catalog/recent-books/me`). 즉 EKS `${API_HOST}`로 `/api/books`를 호출하면 **404**입니다. 프론트엔드는 이미 `/api/catalog/wishlist/me`처럼 올바른 경로로 고쳐서 쓰고 있고(`README.md` "프론트엔드 영향" 참고), 이 Ingress YAML만 안 따라갔습니다. `k6/README.md` "실행 전 반드시 확인할 것" #1에서도 같은 문제를 지적합니다. **이 문서는 현재 배포된 그대로를 기록한 것이며, 실제 정상 동작하는 경로는 `/api/catalog/...`입니다.**

## 4. 오토스케일링 (HPA) 명세

| **서비스** | **Target** | **Min → Max** | **스케일링 메트릭 기준** | **오토스케일링 동작 특징** |
| -- | -- | -- | -- | -- |
| **catalog** | catalog-deployment | 2 → 20 | CPU 70% + Memory 80% | 기본 확장 behavior 적용 |
| **member** | member-deployment | 2 → 6 | CPU 70% | DB 커넥션 증가 방지를 위해 상한 보수적 설정 |
| **order** | order-deployment | 2 → 30 | CPU 70% | **Scale Up**: 15초당 100% 확장 (`stabilizationWindowSeconds: 0`)**Scale Down**: `stabilizationWindowSeconds: 300`*상한 30 산정: CP 10 × 30 = 300 커넥션 이내* |
| **ai-rag** | ai-rag-deployment | 1 → 6 | CPU 70% (안전망) | 외부 API 호출 위주 I/O 워크로드. CPU는 비상 임계값 |
| **ai-bot** | ai-bot-deployment | 1 → 10 | Custom: `http_server_requests_active`*(AverageValue: 10)* | 외부 LLM API 대기 워크로드.**Scale Up**: **15초당 100% 확장 (**`stabilizationWindowSeconds: 0`) (급격한 트래픽 즉시 대응) |

## 5. ConfigMap & Secret 명세

### 5.1 환경변수 및 주입 구조

| **ConfigMap / Secret** | **주요 키 목록** | **주입 방식 및 특이사항** |
| -- | -- | -- |
| **catalog-config** | SPRING_PROFILES_ACTIVE, SERVER_PORT=8080, DB_HOST(Reader), DB_PORT, DB_NAME, SERVICES_ORDER_URL, REDIS_HOST, AWS_REGION, AWS_COGNITO_REGION, AWS_COGNITO_USER_POOL_ID | `envFrom.configMapRef` |
| **member-config** | 위와 동일 패턴 (DB_HOST는 Primary) | `envFrom.configMapRef` |
| **order-config** | 위와 동일 패턴 (DB_HOST는 Primary, REDIS_HOST 이벤트 채널 포함) | `envFrom.configMapRef` |
| **ai-config** | AI_DB_HOST(Primary), AI_DB_NAME, AWS_BEDROCK_REGION, AI_VECTOR_BUCKET, AI_VECTOR_INDEX="wiki-v1", AI_LLM_MODEL_RAG, AI_LLM_MODEL_BOT, AI_MAX_DISTANCE="0.75", AI_DAILY_QUOTA="50", REDIS_HOST 등 | `envFrom.configMapRef` 후, Deployment에서 `AI_LLM_MODEL` 값 오버라이드 |
| **catalog-secret** | DB_USERNAME, DB_PASSWORD | `envFrom.secretRef` (Opaque, envsubst 주입) |
| **order-secret** | DB_USERNAME, DB_PASSWORD, `KAKAOPAY_SECRET_KEY` | 동일 |
| **member-secret** | DB_USERNAME, DB_PASSWORD, AWS_COGNITO_CLIENT_ID, AWS_COGNITO_CLIENT_SECRET | 동일 (Cognito 자격증명은 member 전용) |
| **ai-secret** | AI_DB_USERNAME, AI_DB_PASSWORD | 동일 |

> ⚠️ **Amazon Bedrock 모델 ID 리전 제약**:
>
> * **RAG 모델**: `global.anthropic.claude-haiku-4-5-20251001-v1:0` (ap-northeast-2, us-east-1 ACTIVE)
> * **Bot 모델**: `apac.amazon.nova-micro-v1:0`
> * 해당 모델은 `INFERENCE_PROFILE` 전용으로 **리전 접두사(**`global.`/`apac.`)가 제외된 단독 모델 ID 호출 시 요청이 거부됩니다.

### 5.2 DB 및 외부 인프라 연동

| **서비스** | **DB_HOST (Service/Endpoint)** | **외부 SaaS / AWS 연동 항목** |
| -- | -- | -- |
| **catalog** | `db-reader-service.lion-db.svc.cluster.local:5432` | Redis (`REDIS_HOST`), Cognito, 내부 서비스 (`order-service:8080`) |
| **member** | `db-primary-service.lion-db.svc.cluster.local:5432` | Redis, Cognito (ClientId, ClientSecret 포함) |
| **order** | `db-primary-service.lion-db.svc.cluster.local:5432` | Redis (리뷰 권한 이벤트), Cognito, RDS Proxy (order 전용) |
| **ai** | `db-primary-service.lion-db.svc.cluster.local:5432` | Redis (검색 대상 도서 목록, Quota), S3 Vectors (`AI_VECTOR_BUCKET`), Amazon Bedrock |

**Aurora ExternalName 서비스 매핑** (`k8s/base/04-db.yaml`, namespace: `lion-db`)

| **Service Name** | **Type** | **Target Endpoint** | **Port** |
| -- | -- | -- | -- |
| **db-primary-service** | ExternalName | `${AURORA_ENDPOINT}` (Writer) | 5432 |
| **db-reader-service** | ExternalName | `${AURORA_READER_ENDPOINT}` (Reader) | 5432 |

## 6. NetworkPolicy 명세

| **Policy명** | **대상 Pod** | **Ingress 허용 규칙** | **Egress 허용 규칙** |
| -- | -- | -- | -- |
| **lion-app-baseline** | `tier: backend` (전체) | • `ingress-nginx`, `kube-system`&#10;• `${VPC_CIDR}` (kubelet probe)&#10;• 동일 네임스페이스 내 `tier: backend` | • CoreDNS (UDP/TCP 53)&#10;• 동일 네임스페이스 내 `tier: backend`&#10;• Aurora (TCP 5432) / Redis (TCP 6379)&#10;• 외부 AWS HTTPS (TCP 443) |
| **order-internal-allowlist** | `app: order-service` | • `app: catalog-service` Pod&#10;• `ingress-nginx`&#10;• `${VPC_CIDR}` | baseline 정책 상속 |

> ✅ **\[수정 완료\] 아래는 발견 당시의 문제와 해결 방식 기록**
>
> * **의도**: `order-internal-allowlist`는 `order-service` 내 `/internal/**` 재고 API 호출 권한을 `catalog-service`로 엄격히 제한하기 위해 작성되었습니다.
> * **문제였던 것**: `lion-app-baseline`이 동일 네임스페이스 내 전체 백엔드(`tier: backend`) 통신을 포괄적으로 허용하고 있어서, NetworkPolicy가 **합집합(OR)**으로 적용되는 특성상 `order-internal-allowlist`가 실질적인 차단 효과를 내지 못했습니다.
> * **현재 상태 (`k8s/base/09-networkpolicy.yaml`)**: `order-service`를 `lion-app-baseline`의 대상 Pod 셀렉터에서 제외하고, 별도 정책으로 완전히 대체하는 방식으로 수정되었습니다. 더 이상 두 정책이 겹치지 않아 의도한 대로 격리됩니다.

## 7. 주요 아키텍처 설계 결정 사항

* **Aurora 단일 클러스터 통합 구성**: Vector 데이터의 S3 Vectors 이관에 따라 별도 `ai_db` 클러스터 분리안을 폐기하고, 4개 스키마 분할 및 서비스별 DB 계정 권한 격리 방식을 채택하여 인프라 비용을 최적화했습니다.
* **RDS Proxy 선별 적용**: `ai_db` Serverless v2 Auto-Pause 기능과의 충돌을 방지하기 위해 트랜잭션이 집중되는 `order-service` 영역에만 RDS Proxy를 단독 연결했습니다.
* **단일 Ingress 기반 라우팅**: ALB Ingress의 L7 경로 라우팅을 활용하여 별도 API Gateway 구축에 따른 SPOF 및 아키텍처 복잡도를 단축했습니다.
* **AI 워크로드 물리 분리**: 단일 이미지를 기반으로 Deployment, Service, HPA 및 리소스 프로파일을 격리 배포하여 RAG 시스템과 문의봇 간 장애 전이 차단 및 인프라 비용 절감을 달성했습니다.
* **워크로드 특성별 QoS 다변화**:
  * **order-service**: 결제/재고 트랜잭션의 안정성을 보장하기 위해 **Guaranteed Class**를 부여하여 자원 경합에 따른 Eviction을 방지했습니다.
  * **ai-rag**: 소수 사용자의 간헐적 버스트 패턴에 대응하도록 **Burstable Class**를 지정하여 평시 자원 점유율을 낮췄습니다.
* **커스텀 메트릭 기반 오토스케일링 전제**: 외부 LLM API 대기 지연이 발생하는 워크로드(`ai-bot`, `ai-rag`)는 CPU 기준 HPA가 무력화되므로, Active Request 수 기반의 KEDA/Prometheus Adapter 메트릭 스케일링을 전제로 설계되었습니다.
