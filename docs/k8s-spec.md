# Book-Eating-Lion Kubernetes 배포 명세서

> 스캔 대상: `k8s/base/`, `k8s/catalog/`, `k8s/order/`, `k8s/member/`, `k8s/ai/` (전체 21개 YAML)
> 기준 브랜치/커밋: `fix/order-service` (`a264603`) · 문서 갱신: 2026-08-12
> 본 문서는 매니페스트에 명시된 리터럴 값·주석만을 근거로 작성되었으며, 추측성 서술은 배제한다. `${VAR}` 형태는 CD 워크플로가 GitHub Secrets로 `envsubst` 치환하는 플레이스홀더다.

---

## 1. 스캔한 매니페스트 목록

| 디렉터리 | 파일명 | Kind | 비고 |
|---|---|---|---|
| `k8s/base/` | `01-namespace.yaml` | Namespace × 2 | `lion-app`, `lion-db` |
| `k8s/base/` | `03-secret.yaml` | Secret × 4 | catalog/order/member/ai |
| `k8s/base/` | `04-db.yaml` | Service(ExternalName) × 2 | Aurora Writer/Reader |
| `k8s/base/` | `08-ingress.yaml` | Ingress × 1 | 유일한 외부 진입점 |
| `k8s/base/` | `09-networkpolicy.yaml` | NetworkPolicy × 2 | baseline + order 전용 |
| `k8s/catalog/` | `configmap.yaml` | ConfigMap × 1 | |
| `k8s/catalog/` | `deployment.yaml` | Deployment × 1 | |
| `k8s/catalog/` | `hpa.yaml` | HPA × 1 | |
| `k8s/catalog/` | `service.yaml` | Service × 1 | |
| `k8s/member/` | `configmap.yaml` | ConfigMap × 1 | |
| `k8s/member/` | `deployment.yaml` | Deployment × 1 | |
| `k8s/member/` | `hpa.yaml` | HPA × 1 | |
| `k8s/member/` | `service.yaml` | Service × 1 | |
| `k8s/order/` | `configmap.yaml` | ConfigMap × 1 | |
| `k8s/order/` | `deployment.yaml` | Deployment × 1 | |
| `k8s/order/` | `hpa.yaml` | HPA × 1 | 커스텀 scaling behavior 보유 |
| `k8s/order/` | `service.yaml` | Service × 1 | |
| `k8s/ai/` | `configmap.yaml` | ConfigMap × 1 | rag/bot 공유 |
| `k8s/ai/` | `deployment-bot.yaml` | Deployment × 1 | |
| `k8s/ai/` | `deployment-rag.yaml` | Deployment × 1 | |
| `k8s/ai/` | `hpa-bot.yaml` | HPA × 1 | 커스텀 Pods 메트릭 |
| `k8s/ai/` | `hpa-rag.yaml` | HPA × 1 | |
| `k8s/ai/` | `service.yaml` | Service × 2 | `ai-rag`, `ai-bot` |

> `02-*`, `05~07-*` 번호는 디렉터리에 존재하지 않는 결번이다 — 파일명 프리픽스는 적용 순서 힌트일 뿐 연속성을 보장하지 않는다.

---

## 2. 클러스터 및 아키텍처 개요

- **네임스페이스 2개**: `lion-app`(4개 백엔드 서비스 + Ingress + NetworkPolicy), `lion-db`(Aurora ExternalName 전용, 레이블 `access-to-db: allowed`).
- **DB**: PostgreSQL 단일 Aurora 클러스터. `catalog_db`/`order_db`/`member_db`/`ai_db` 4개 스키마로 분할, 격리는 클러스터가 아닌 **서비스별 DB 계정 권한**으로 구현.
- **외부 진입점**: `ingress-nginx` 기반 단일 Ingress(`lion-ingress`)뿐 — 별도 API Gateway(Spring Cloud Gateway) 미도입, SPOF 최소화 목적.
- **프런트엔드**: S3 + CloudFront로 별도 서빙(이 Ingress는 `/api/*`만 담당).
- **서비스 디스커버리**: Eureka 미사용, Kubernetes Service DNS(`*.lion-app.svc.cluster.local`, `*.lion-db.svc.cluster.local`)로 해결.
- **AI 워크로드 분리**: 경로 기준으로 `ai-rag`(질의/RAG)와 `ai-bot`(문의봇)이 동일 이미지를 쓰되 별도 Deployment·Service·HPA·리소스 프로파일을 가짐.

---

## 3. 서비스별 자원 및 프로브 명세

### 3.1 컨테이너 자원 정책 (QoS & Resource)

| 서비스 | Requests (CPU/MEM) | Limits (CPU/MEM) | QoS Class | 설정 근거/특이사항 |
|---|---|---|---|---|
| catalog | 250m / 512Mi | 1000m / 1Gi | Burstable | 읽기 95% 캐시 친화적 워크로드 |
| member | 200m / 384Mi | 500m / 768Mi | Burstable | 경량(BCrypt+JWT 검증만) — 큰 자원 불필요 |
| order | 500m / 1Gi | 500m / 1Gi | **Guaranteed** | requests=limits 강제 — 결제/재고 트랜잭션 도중 throttle·evict 방지 |
| ai-rag | 500m / 512Mi | 2000m / 2Gi | Burstable(격차 큼) | 프리미엄 소수 사용자의 간헐적 버스트 패턴에 대응 |
| ai-bot | 100m / 384Mi | 500m / 768Mi | Burstable | 외부 LLM API 대기 위주라 CPU 요구량 최소 |

### 3.2 헬스체크 및 배포 전략 (Probes & Strategy)

| 서비스 | Startup Probe | Readiness/Liveness | Termination Period | 배포 전략 |
|---|---|---|---|---|
| catalog | `/actuator/health/readiness`, 5s 간격, threshold 36(≈3분) | 두 프로브 모두 동일 경로 패턴, 10s 간격, threshold 3 | 30s | RollingUpdate, maxSurge 1 / maxUnavailable 0 |
| member | `/actuator/health/readiness`, 5s 간격, threshold **12(≈1분)** | 동일(10s / threshold 3) | 30s | RollingUpdate, maxSurge 1 / maxUnavailable 0 |
| order | `/actuator/health/readiness`, 5s 간격, threshold 36(≈3분) | 동일(10s / threshold 3) | 30s | RollingUpdate, maxSurge 1 / maxUnavailable 0 |
| ai-rag | `/actuator/health/readiness`, 5s 간격, threshold 36(≈3분) | 동일(10s / threshold 3) | 30s | RollingUpdate, maxSurge 1 / maxUnavailable 0 |
| ai-bot | `/actuator/health/readiness`, 5s 간격, threshold 36(≈3분) | 동일(10s / threshold 3) | 30s | RollingUpdate, maxSurge 1 / maxUnavailable 0 |

> Readiness/Liveness는 `/actuator/health/readiness` · `/actuator/health/liveness`(포트명 `http`=8080)로 전 서비스 공통. member만 startupProbe 상한이 짧은 이유는 "무거운 초기화가 없다"는 주석 근거(RAG를 member에 두지 않은 설계와 연결).
> `replicas` 필드는 4개 Deployment 모두 미기재 — HPA가 소유(직접 apply 시 HPA가 늘려둔 값을 덮어쓰는 충돌 방지).
> Pod Anti-Affinity(`preferredDuringSchedulingIgnoredDuringExecution`, weight 100, `topologyKey: kubernetes.io/hostname`)는 catalog/member/order 3개 Deployment에만 존재하며, **ai-rag/ai-bot에는 없음**.

---

## 4. Ingress L7 라우팅 명세

Ingress: `lion-ingress`(`lion-app`), `ingressClassName: nginx`, Host `${API_HOST}`. CORS: `enable-cors=true`, `allow-origin=${FRONTEND_ORIGIN}`, `allow-methods=GET,POST,PUT,PATCH,DELETE,OPTIONS`, `allow-headers=Authorization,Content-Type,X-Member-Id`, `allow-credentials=false`. 규칙은 매니페스트 상단부터 순서대로 매칭.

| Path (`pathType: Prefix`) | Backend Service | Target Port | 비고/설계 의도 |
|---|---|---|---|
| `/api/ai/ask` | `ai-rag` | 8080 | RAG 질의 |
| `/api/ai/lion` | `ai-rag` | 8080 | RAG 질의 |
| `/api/ai/bot` | `ai-bot` | 8080 | 문의봇, 별도 Pod 집합이라 봇 폭주가 RAG에 전이되지 않음 |
| `/api/orders` | `order-service` | 8080 | |
| `/api/cart` | `order-service` | 8080 | |
| `/api/coupons` | `order-service` | 8080 | |
| `/api/payments` | `order-service` | 8080 | |
| `/api/members` | `member-service` | 8080 | |
| `/api/auth` | `member-service` | 8080 | |
| `/api/cards` | `member-service` | 8080 | |
| `/api/books` | `catalog-service` | 8080 | |
| `/api/reviews` | `catalog-service` | 8080 | |
| `/api/wishlist` | `catalog-service` | 8080 | `/api/members/me/*` 하위가 아닌 최상위 경로 — 접두사 공유 시 경로 길이 의존 라우팅 문제 회피 |
| `/api/recent-books` | `catalog-service` | 8080 | 위와 동일 사유 |

> `/internal/**`은 Ingress에 의도적으로 미등록 — 외부에서 도달 불가능해야 하는 서비스 간 전용 경로이며, §7의 NetworkPolicy가 2차 방어선.

---

## 5. 오토스케일링 (HPA) 명세

| 서비스 | Deployment | HPA명 | Min → Max | 확장 메트릭 기준 | 오토스케일링 동작 특징 |
|---|---|---|---|---|---|
| catalog | `catalog-deployment` | `catalog-hpa` | 2 → 20 | CPU 70% + Memory 80% (Resource) | 읽기 95% 캐시 친화적, 기본 behavior |
| member | `member-deployment` | `member-hpa` | 2 → 6 | CPU 70% (Resource) | 상한 최소 폭 — replica 증가 = Aurora 커넥션 배증이라 보수적으로 설정 |
| order | `order-deployment` | `order-hpa` | 2 → 30 | CPU 70% (Resource) | **커스텀 behavior**: scaleUp `stabilizationWindowSeconds:0` + `Percent 100%/15s`(급격한 결제 부하 즉시 대응) / scaleDown `stabilizationWindowSeconds:300`(트랜잭션 중 Pod 성급한 축소 방지). 상한 30 근거: `pool 10 × 30 = 300` 커넥션이 RDS Proxy 없이 버틸 수 있는 한계 |
| ai-rag | `ai-rag-deployment` | `ai-rag-hpa` | 1 → 6 | CPU 70% (Resource, **안전망 용도**) | 임베딩을 Bedrock Titan(외부 API)으로 확정해 I/O 바운드 — CPU는 폭주 감지용일 뿐, 실질 확장 기준은 동시 요청 수(KEDA/Prometheus Adapter 도입 전) 예정 |
| ai-bot | `ai-bot-deployment` | `ai-bot-hpa` | 1 → 10 | **Pods 커스텀 메트릭**: `http_server_requests_active`, `AverageValue: 10` | 외부 LLM API 대기로 CPU가 오르지 않는 워크로드 대표 사례. `Prometheus Adapter`/`KEDA` 미설치 시 `ScalingActive=False`로 무력화됨 |

> 서비스별 상한이 다른 것은 "핀포인트 확장" 시연 근거(주석): 결제 부하 시 order만 30개로 확장, 나머지는 최소 replica 유지.

---

## 6. ConfigMap & Secret 명세

### 6.1 환경변수 및 주입 구조

| ConfigMap/Secret명 | Namespace | 주요 키 목록 | 주입 방식 및 특이사항 |
|---|---|---|---|
| `catalog-config` | lion-app | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT=8080`, `DB_HOST`(Reader), `DB_PORT`, `DB_NAME`, `SERVICES_ORDER_URL`, `REDIS_HOST`, `AWS_REGION`, `AWS_COGNITO_REGION`, `AWS_COGNITO_USER_POOL_ID` | `envFrom.configMapRef` |
| `member-config` | lion-app | 위와 동일 패턴, `DB_HOST`(Primary) | `envFrom.configMapRef` |
| `order-config` | lion-app | 위와 동일 패턴, `DB_HOST`(Primary), `REDIS_HOST`(리뷰 권한 이벤트 채널) | `envFrom.configMapRef` |
| `ai-config` | lion-app | `AI_DB_HOST`(Primary), `AI_DB_NAME`, `AWS_BEDROCK_REGION`, `AI_VECTOR_BUCKET`, `AI_VECTOR_INDEX="wiki-v1"`, `AI_LLM_MODEL_RAG`, `AI_LLM_MODEL_BOT`, `AI_MAX_DISTANCE="0.75"`(잠정값), `AI_DAILY_QUOTA="50"`, `REDIS_HOST` 외 | `envFrom.configMapRef` + rag/bot 각 Deployment가 `env.valueFrom.configMapKeyRef`로 `AI_LLM_MODEL_RAG`/`AI_LLM_MODEL_BOT` 중 하나를 `AI_LLM_MODEL`로 선택 오버라이드(`env`가 `envFrom`보다 우선) |
| `catalog-secret` | lion-app | `DB_USERNAME`, `DB_PASSWORD` | `envFrom.secretRef`, `type: Opaque`, 값은 GitHub Secrets → `envsubst` 주입 |
| `order-secret` | lion-app | `DB_USERNAME`, `DB_PASSWORD` | 위와 동일 |
| `member-secret` | lion-app | `DB_USERNAME`, `DB_PASSWORD`, `AWS_COGNITO_CLIENT_ID`, `AWS_COGNITO_CLIENT_SECRET` | 위와 동일. Cognito Client 자격증명은 member만 보유 |
| `ai-secret` | lion-app | `AI_DB_USERNAME`, `AI_DB_PASSWORD` | 위와 동일 |

> Bedrock 모델 ID는 리전 제약으로 접두사 필수: RAG용 `global.anthropic.claude-haiku-4-5-20251001-v1:0`(ap-northeast-2/us-east-1 양쪽 ACTIVE), 봇용 `apac.amazon.nova-micro-v1:0`. `inferenceTypesSupported=[INFERENCE_PROFILE]`뿐이라 접두사 없는 맨 모델 ID 호출은 거부됨.

### 6.2 DB 및 외부 인프라 연동

| 서비스 | DB_HOST (Service/Endpoint) | 외부 SaaS/AWS 연동 항목 |
|---|---|---|
| catalog | `db-reader-service.lion-db.svc.cluster.local:5432` | Redis(`REDIS_HOST`), Cognito(Region/UserPoolId), 내부 호출 `order-service:8080` |
| member | `db-primary-service.lion-db.svc.cluster.local:5432` | Redis, Cognito(Region/UserPoolId/ClientId/ClientSecret) |
| order | `db-primary-service.lion-db.svc.cluster.local:5432` | Redis(리뷰 권한 이벤트), Cognito(Region/UserPoolId), RDS Proxy(order 전용) |
| ai | `db-primary-service.lion-db.svc.cluster.local:5432`(`AI_DB_HOST`) | Redis(먹인 책 목록 캐시), S3 Vectors(`AI_VECTOR_BUCKET`/`AI_VECTOR_INDEX`), AWS Bedrock(LLM), Cognito(Region/UserPoolId) — 질의 경로(`POST /api/ai/ask`)는 DB 미경유, DB는 먹이기/문의 등 쓰기 전용 |

**Aurora ExternalName 매핑** (`k8s/base/04-db.yaml`, namespace `lion-db`)

| Service | Type | 대상 | 포트 |
|---|---|---|---|
| `db-primary-service` | ExternalName | `${AURORA_ENDPOINT}` (Writer) | 5432 |
| `db-reader-service` | ExternalName | `${AURORA_READER_ENDPOINT}` (Reader) | 5432 |

---

## 7. NetworkPolicy 명세

| Policy명 | 대상 Pod (Selector) | Ingress 허용 규칙 | Egress 허용 규칙 |
|---|---|---|---|
| `lion-app-baseline` | `tier: backend` (전체) | `ingress-nginx`/`kube-system` 네임스페이스 + `${VPC_CIDR}`(kubelet probe 노드 IP 대역) + 동일 네임스페이스 `tier: backend` — 전부 TCP 8080 | CoreDNS(`kube-system`, UDP/TCP 53) + 동일 네임스페이스 `tier: backend`(TCP 8080) + Aurora(TCP 5432)/Redis(TCP 6379) + S3/ECR/Bedrock 등 외부 HTTPS(TCP 443) |
| `order-internal-allowlist` | `app: order-service` | `app: catalog-service` Pod + `ingress-nginx` 네임스페이스 + `${VPC_CIDR}` — TCP 8080 | (baseline 상속, 별도 미지정) |

> `order-internal-allowlist`는 order-service의 `/internal/**` 재고 API를 catalog-service만 호출 가능하게 하는 2차 방어선(1차는 §4의 Ingress 경로 미등록).
> kubelet 헬스체크는 Pod가 아닌 노드 IP에서 발신되어 `namespaceSelector`로 매칭되지 않으므로 `ipBlock: ${VPC_CIDR}`를 별도로 열어야 하며, 누락 시 정책 강제 CNI 환경에서 전 Pod가 동시에 `NotReady`로 전환될 수 있다.
> NetworkPolicy는 CNI가 지원해야 유효(EKS 기본 VPC CNI는 최신 버전에서 지원); 미지원 클러스터에서는 리소스가 생성돼도 무효과.

---

## 8. 주요 설계 결정 및 아키텍처 특징 요약

- **Aurora 단일 클러스터, ai_db 별도 클러스터 분리는 폐기**: pgvector 불요(벡터는 S3 Vectors로 이관), Serverless v2 auto-pause만으로는 별도 클러스터 비용 정당화 불가. 격리는 클러스터가 아닌 스키마 4분할 + 서비스별 DB 계정 권한으로 구현.
- **order만 RDS Proxy 연결**: ai_db의 Serverless v2 auto-pause와 상충하기 때문에 order 전용으로 한정.
- **API Gateway 미도입**: ALB Ingress의 경로 라우팅으로 충분하다고 판단, 게이트웨이 추가는 SPOF 증가 우려.
- **ai-rag/ai-bot은 이미지 동일, 워크로드만 분리**: Deployment/Service/HPA/리소스 프로파일만 나눠 자원 격리·장애 격리를 달성 — 완전한 마이크로서비스 분리보다 약 10배 저비용(주석 표현)으로 동일 효과.
- **order는 Guaranteed QoS, ai-rag는 정반대(Burstable, requests≪limits)**: 결제/재고 트랜잭션은 자원 경합 시 throttle·evict를 허용할 수 없는 반면, ai-rag는 소수 프리미엄 사용자의 간헐적 버스트 패턴에 맞춰 평시 자원 점유를 최소화.
- **CPU 기반 HPA가 무력한 워크로드(ai-bot, 향후 ai-rag)는 커스텀 메트릭 전제**: 외부 LLM API 대기 중에는 CPU가 오르지 않으므로 Pods 메트릭(`http_server_requests_active`) 또는 동시 요청 수 기반 확장이 필요하며, 이는 Prometheus Adapter/KEDA 도입을 전제로 한다(미도입 시 Bulkhead 스레드풀 격리가 유일한 방어선).
