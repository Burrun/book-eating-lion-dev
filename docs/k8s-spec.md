# Kubernetes 배포 명세서 (Book-Eating-Lion)

> `k8s/` 디렉터리 내 전체 매니페스트(21개 YAML)를 리버스 엔지니어링하여 작성한 명세서.
> 코드/주석에 명시된 값만 기술하며, 추측성 서술은 배제한다.
> 최종 갱신: 2026-08-12 / 대상 커밋: `fix/order-service` 브랜치 (`a264603`)

---

## 1. 스캔한 매니페스트 목록 (21개)

| 디렉터리 | 파일 | Kind |
|---|---|---|
| `k8s/base/` | `01-namespace.yaml` | Namespace × 2 |
| `k8s/base/` | `03-secret.yaml` | Secret × 4 |
| `k8s/base/` | `04-db.yaml` | Service(ExternalName) × 2 |
| `k8s/base/` | `08-ingress.yaml` | Ingress × 1 |
| `k8s/base/` | `09-networkpolicy.yaml` | NetworkPolicy × 2 |
| `k8s/catalog/` | `configmap.yaml` | ConfigMap × 1 |
| `k8s/catalog/` | `deployment.yaml` | Deployment × 1 |
| `k8s/catalog/` | `hpa.yaml` | HorizontalPodAutoscaler × 1 |
| `k8s/catalog/` | `service.yaml` | Service × 1 |
| `k8s/member/` | `configmap.yaml` | ConfigMap × 1 |
| `k8s/member/` | `deployment.yaml` | Deployment × 1 |
| `k8s/member/` | `hpa.yaml` | HorizontalPodAutoscaler × 1 |
| `k8s/member/` | `service.yaml` | Service × 1 |
| `k8s/order/` | `configmap.yaml` | ConfigMap × 1 |
| `k8s/order/` | `deployment.yaml` | Deployment × 1 |
| `k8s/order/` | `hpa.yaml` | HorizontalPodAutoscaler × 1 |
| `k8s/order/` | `service.yaml` | Service × 1 |
| `k8s/ai/` | `configmap.yaml` | ConfigMap × 1 |
| `k8s/ai/` | `deployment-bot.yaml` | Deployment × 1 |
| `k8s/ai/` | `deployment-rag.yaml` | Deployment × 1 |
| `k8s/ai/` | `hpa-bot.yaml` | HorizontalPodAutoscaler × 1 |
| `k8s/ai/` | `hpa-rag.yaml` | HorizontalPodAutoscaler × 1 |
| `k8s/ai/` | `service.yaml` | Service × 2 (`ai-rag`, `ai-bot`) |

`02-*`, `05~07-*` 번호는 디렉터리에 존재하지 않는다(결번 — 파일명 프리픽스는 적용 순서 힌트일 뿐 연속성을 보장하지 않음).

값은 실제 리터럴이 없는 항목이 많다: `${VAR}` 형태는 GitHub Actions CD 워크플로가 `envsubst`로 치환한 뒤 `kubectl apply`하는 템플릿 플레이스홀더이며, 리포지터리에는 실값이 커밋되지 않는다.

---

## 2. 클러스터 개요

- **네임스페이스 2개**
  - `lion-app` — 4개 백엔드 서비스(order/catalog/member/ai)와 Ingress, NetworkPolicy가 위치.
  - `lion-db` — Aurora PostgreSQL(RDS)을 `ExternalName` Service로 감싸는 전용 네임스페이스. 레이블 `access-to-db: allowed`.
- **DB 엔진**: PostgreSQL 단일 Aurora 클러스터, 스키마 4분할(`catalog_db` / `order_db` / `member_db` / `ai_db`), 서비스별 DB 계정으로 스키마 간 접근을 차단(클러스터 분리가 아닌 계정 권한으로 격리).
- **외부 진입점**: `ingress-nginx` 컨트롤러 기반 단일 Ingress(`lion-ingress`). 별도 API Gateway(Spring Cloud Gateway) 없음 — SPOF 최소화 목적.
- **프런트엔드**: S3 + CloudFront로 별도 서빙(이 Ingress는 `/api/*` 경로만 다룸).
- **서비스 디스커버리**: Eureka 미사용, Kubernetes Service DNS(`*.lion-app.svc.cluster.local`, `*.lion-db.svc.cluster.local`)로 해결.
- **AI 워크로드는 경로 기준으로 Deployment/Service가 2계열로 분리**: `ai-rag`(RAG 질의)와 `ai-bot`(문의봇)이 동일 이미지를 사용하되 별도 리소스 프로파일·별도 HPA·별도 Service를 가짐.

---

## 3. 계층별 구성표

### 3.1 Namespace

| 이름 | 레이블 | 용도 |
|---|---|---|
| `lion-app` | - | 애플리케이션 워크로드 전체 |
| `lion-db` | `access-to-db: allowed` | Aurora ExternalName Service 전용 |

### 3.2 Secret (`k8s/base/03-secret.yaml`, 모두 `lion-app`, `type: Opaque`)

| Secret | 키 |
|---|---|
| `catalog-secret` | `DB_USERNAME`, `DB_PASSWORD` |
| `order-secret` | `DB_USERNAME`, `DB_PASSWORD` |
| `member-secret` | `DB_USERNAME`, `DB_PASSWORD`, `AWS_COGNITO_CLIENT_ID`, `AWS_COGNITO_CLIENT_SECRET` |
| `ai-secret` | `AI_DB_USERNAME`, `AI_DB_PASSWORD` |

값은 전부 `${...}` 플레이스홀더이며 CD 파이프라인의 GitHub Secrets가 주입한다.

### 3.3 서비스별 컨테이너 리소스 정책

| 서비스 | requests (cpu/mem) | limits (cpu/mem) | QoS Class | 비고 |
|---|---|---|---|---|
| catalog | 250m / 512Mi | 1000m / 1Gi | Burstable | |
| member | 200m / 384Mi | 500m / 768Mi | Burstable | 경량(BCrypt+JWT 검증만) |
| order | 500m / 1Gi | 500m / 1Gi | **Guaranteed** | requests=limits로 강제 — 결제/재고 트랜잭션 도중 throttle·evict 방지 |
| ai-rag | 500m / 512Mi | 2000m / 2Gi | Burstable(격차 큼) | 간헐적 고부하(버스트) 패턴 |
| ai-bot | 100m / 384Mi | 500m / 768Mi | Burstable | 외부 LLM API 대기 위주라 CPU 최소 요구 |

### 3.4 프로브 공통 패턴 (모든 Deployment)

- `startupProbe` / `readinessProbe` / `livenessProbe` 모두 `httpGet` 대상 동일: readiness는 `/actuator/health/readiness`, liveness는 `/actuator/health/liveness`, 포트는 컨테이너 포트명 `http`(8080).
- `readinessProbe`/`livenessProbe`: `periodSeconds: 10`, `failureThreshold: 3` (공통).
- `startupProbe`: `periodSeconds: 5`, `failureThreshold`는 서비스별로 상이 — catalog/order/ai-bot/ai-rag는 `36`(=3분 상한), member만 `12`(=1분 상한, "무거운 초기화 없음"이 근거).
- `terminationGracePeriodSeconds: 30` (전 서비스 공통).
- 배포 전략: 전 서비스 `RollingUpdate`, `maxSurge: 1`, `maxUnavailable: 0`.
- `replicas` 필드는 4개 Deployment 모두 명시하지 않음 — HPA가 소유(직접 apply 시 HPA가 늘려둔 값을 덮어쓰는 충돌 방지).
- Pod Anti-Affinity: catalog/member/order 3개 Deployment에 `preferredDuringSchedulingIgnoredDuringExecution`(weight 100, `topologyKey: kubernetes.io/hostname`)로 동일 앱 Pod의 동일 노드 배치를 회피 권고. **ai-rag/ai-bot에는 이 affinity 블록이 없음.**

---

## 4. Ingress L7 라우팅 명세 (`k8s/base/08-ingress.yaml`)

- Ingress 이름: `lion-ingress` (namespace `lion-app`), `ingressClassName: nginx`
- Host: `${API_HOST}` (템플릿)
- CORS 애노테이션: `enable-cors: true`, `cors-allow-origin: ${FRONTEND_ORIGIN}`, `cors-allow-methods: GET, POST, PUT, PATCH, DELETE, OPTIONS`, `cors-allow-headers: Authorization, Content-Type, X-Member-Id`, `cors-allow-credentials: false`
- 규칙은 매니페스트 상단부터 순서대로 매칭(더 구체적인 경로가 위에 위치).

| Path (`pathType: Prefix`) | Backend Service | Target Port |
|---|---|---|
| `/api/ai/ask` | `ai-rag` | 8080 |
| `/api/ai/lion` | `ai-rag` | 8080 |
| `/api/ai/bot` | `ai-bot` | 8080 |
| `/api/orders` | `order-service` | 8080 |
| `/api/cart` | `order-service` | 8080 |
| `/api/coupons` | `order-service` | 8080 |
| `/api/payments` | `order-service` | 8080 |
| `/api/members` | `member-service` | 8080 |
| `/api/auth` | `member-service` | 8080 |
| `/api/cards` | `member-service` | 8080 |
| `/api/books` | `catalog-service` | 8080 |
| `/api/reviews` | `catalog-service` | 8080 |
| `/api/wishlist` | `catalog-service` | 8080 |
| `/api/recent-books` | `catalog-service` | 8080 |

**설계 노트 (매니페스트 주석 기반)**
- AI 서비스는 Service가 1개가 아니라 경로별로 `ai-rag`/`ai-bot` 2개 Service·2개 Deployment로 분리되어 있어, 봇 트래픽 폭주가 RAG Pod 부하에 섞이지 않는다.
- 찜/최근본상품(`/api/wishlist`, `/api/recent-books`)이 `/api/members/me/*` 하위 경로가 아닌 `catalog-service` 전용 최상위 경로인 이유: 동일 접두사를 두 서비스가 나눠 가지면 라우팅이 경로 길이 비교에 의존하게 되는 문제를 피하기 위함.
- `/internal/**` 경로는 Ingress에 의도적으로 미등록 — 외부에서 도달 불가능한 서비스 간 전용 경로이며, `09-networkpolicy.yaml`이 2차 방어선.

---

## 5. 워크로드 & 오토스케일링(HPA) 명세

| 서비스 | Deployment | HPA | min → max | 메트릭 | 상세 |
|---|---|---|---|---|---|
| catalog | `catalog-deployment` | `catalog-hpa` | 2 → 20 | CPU 70%, Memory 80% (Resource) | 읽기 95% 캐시 친화적 워크로드 |
| member | `member-deployment` | `member-hpa` | 2 → 6 | CPU 70% (Resource) | 상한이 가장 좁음 — replica 증가는 Aurora 커넥션 배증과 직결되므로 인증 부하 특성상 낮게 설정 |
| order | `order-deployment` | `order-hpa` | 2 → 30 | CPU 70% (Resource) | behavior 커스텀 적용(하단 참고). "Order Pod 2→50 핀포인트 확장" 요구 대응 자리. 상한 30 근거: connection-pool `maximum-pool-size 10 × 30 = 300`, 그 이상은 RDS Proxy 없이는 Aurora 과부하 |
| ai-rag | `ai-rag-deployment` | `ai-rag-hpa` | 1 → 6 | CPU 70% (Resource, 안전망) | 임베딩을 Bedrock Titan(외부 API, Phase 0-2b 확정)으로 뽑으므로 I/O 바운드 — CPU 메트릭은 폭주 감지용 안전망일 뿐, 실질 확장 기준은 동시 요청 수(KEDA/Prometheus Adapter 미도입 상태) 예정 |
| ai-bot | `ai-bot-deployment` | `ai-bot-hpa` | 1 → 10 | Pods 메트릭: `http_server_requests_active`, `AverageValue: 10` | 외부 LLM API 응답 대기로 CPU가 오르지 않는 워크로드의 대표 사례 — CPU 기반 HPA 무력화 시연 대상. **Prometheus Adapter 또는 KEDA 설치 필요**, 미설치 시 `ScalingActive=False` |

### order-hpa 커스텀 behavior (`k8s/order/hpa.yaml`)
```
scaleUp:   stabilizationWindowSeconds: 0,  Percent 100% / 15s
scaleDown: stabilizationWindowSeconds: 300
```
결제 부하는 급격히 유입되므로 즉시 스케일업하고, 스케일다운은 5분 유예로 트랜잭션 중인 Pod의 성급한 축소를 방지.

### ai-bot-hpa / ai-rag-hpa 공통 주의사항
두 HPA 모두 `Pods` 타입 커스텀 메트릭(ai-bot) 또는 향후 전환 예정(ai-rag)이 Prometheus Adapter/KEDA 의존이며, 이 어댑터가 클러스터에 없으면 HPA가 동작하지 않는다(주석상 명시: "미설치 상태로 배포하려면 이 파일 대신 Bulkhead 격리에만 의존할 것").

### 장애 격리 구조
- **Pod 레벨**: order는 Guaranteed QoS로 노드 자원 경합 시 throttle/evict 열위에서 보호, ai-rag는 Burstable(requests≪limits)로 간헐적 버스트를 허용하되 경합 시 evict 우선순위를 낮게 허용.
- **워크로드 분리**: ai-bot과 ai-rag를 별도 Deployment/Service/HPA로 분리해 봇 폭주가 RAG Pod에 전이되지 않도록 함(같은 Deployment였다면 HPA 메트릭을 하나만 쓸 수 있었음 — 결정적 사유).
- **네트워크 레벨**: `NetworkPolicy`(§7)로 `/internal/**` 접근을 catalog-service Pod로만 제한(order-service 재고 API 보호).
- **애플리케이션 레벨**: ai-rag/ai-bot 둘 다 resilience4j Bulkhead 스레드풀 격리를 전제(주석상 "여유 시 항목이 아니라 필수"로 명시, 코드 확인은 애플리케이션 설정(`application.yml`) 소관).

---

## 6. ConfigMap / Secret 및 DB·외부 연동 구조

### 6.1 Aurora DB — ExternalName 연동 (`k8s/base/04-db.yaml`)

| Service | Namespace | Type | 대상 | 포트 |
|---|---|---|---|---|
| `db-primary-service` | `lion-db` | `ExternalName` | `${AURORA_ENDPOINT}` (Writer) | 5432 |
| `db-reader-service` | `lion-db` | `ExternalName` | `${AURORA_READER_ENDPOINT}` (Reader) | 5432 |

- 단일 Aurora PostgreSQL 클러스터, 스키마 4개(`catalog_db`/`order_db`/`member_db`/`ai_db`)로 분할. 격리는 클러스터 단위가 아니라 **스키마 분할 + 서비스별 DB 계정 권한**으로 구현 — 다른 서비스 계정으로 타 스키마를 조회하면 `permission denied`.
- Pod는 Aurora 엔드포인트를 직접 알지 못하고 `db-primary-service.lion-db.svc.cluster.local` / `db-reader-service.lion-db.svc.cluster.local`만 바라봄. Aurora 주소 변경 시 이 ExternalName만 갱신.

### 6.2 서비스별 DB 라우팅

| 서비스 | `DB_HOST` (ConfigMap) | 근거 |
|---|---|---|
| catalog | `db-reader-service.lion-db.svc.cluster.local` | 읽기 95% 워크로드 → Reader 엔드포인트 |
| member | `db-primary-service.lion-db.svc.cluster.local` | Writer |
| order | `db-primary-service.lion-db.svc.cluster.local` | 쓰기·트랜잭션 정합성, Writer(RDS Proxy는 order 전용) |
| ai | `db-primary-service.lion-db.svc.cluster.local`(`AI_DB_HOST`) | 먹이기/문의 등 쓰기 경로만 DB 사용. 질의(`POST /api/ai/ask`)는 DB 미경유 — Redis(먹인 책 목록) + S3 Vectors(벡터) 조회 |

### 6.3 ConfigMap 요약

| ConfigMap | Namespace | 핵심 키 |
|---|---|---|
| `catalog-config` | lion-app | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT=8080`, `DB_HOST`(Reader), `DB_PORT=5432`, `DB_NAME`, `SERVICES_ORDER_URL=http://order-service.lion-app.svc.cluster.local:8080`, `REDIS_HOST`, `AWS_REGION`, `AWS_COGNITO_REGION`, `AWS_COGNITO_USER_POOL_ID` |
| `member-config` | lion-app | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT=8080`, `DB_HOST`(Primary), `DB_PORT`, `DB_NAME`, `REDIS_HOST`, `AWS_REGION`, `AWS_COGNITO_REGION`, `AWS_COGNITO_USER_POOL_ID` |
| `order-config` | lion-app | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT=8080`, `DB_HOST`(Primary), `DB_PORT`, `DB_NAME`, `REDIS_HOST`(리뷰 권한 이벤트 채널), `AWS_REGION`, `AWS_COGNITO_REGION`, `AWS_COGNITO_USER_POOL_ID` |
| `ai-config` | lion-app | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT=8080`, `AI_DB_HOST`(Primary), `AI_DB_PORT`, `AI_DB_NAME`, `AWS_REGION`, `AWS_BEDROCK_REGION`, `AI_VECTOR_BUCKET`, `AI_VECTOR_INDEX="wiki-v1"`, `AI_LLM_MODEL_RAG="global.anthropic.claude-haiku-4-5-20251001-v1:0"`, `AI_LLM_MODEL_BOT="apac.amazon.nova-micro-v1:0"`, `AI_MAX_DISTANCE="0.75"`(잠정값), `AI_DAILY_QUOTA="50"`, `AWS_COGNITO_REGION`, `AWS_COGNITO_USER_POOL_ID`, `REDIS_HOST` |

### 6.4 환경변수 주입 방식

- 전 서비스 컨테이너에 `envFrom: [configMapRef, secretRef]`로 ConfigMap과 Secret을 통째로 주입.
- 예외: `ai-rag`/`ai-bot` Deployment는 `ai-config`를 공유하면서, 각자 `env.valueFrom.configMapKeyRef`로 `AI_LLM_MODEL` 단일 키를 `AI_LLM_MODEL_RAG` 또는 `AI_LLM_MODEL_BOT`에서 선택적으로 오버라이드(`env`가 `envFrom`보다 우선순위 높음을 활용). 두 Deployment가 같은 ConfigMap을 `envFrom`으로 받는 구조상 동일 키가 두 값을 가질 수 없어 채택된 패턴.
- Bedrock 모델 ID는 리전 제약으로 접두사 필수: RAG용 Haiku 4.5는 `global.` 접두사(ap-northeast-2/us-east-1 양쪽 ACTIVE), 봇용 Nova Micro는 `apac.` 접두사. 두 모델 모두 `inferenceTypesSupported=[INFERENCE_PROFILE]`뿐이라 접두사 없는 맨 모델 ID 호출은 거부됨.

### 6.5 Redis / 외부 서비스

- `REDIS_HOST`: catalog/member/order/ai 4개 서비스 ConfigMap 모두에 존재, 값은 `${REDIS_HOST}` 템플릿(ElastiCache 추정, 매니페스트 내 프로비저닝 리소스는 없음 — 외부 관리형 리소스로 값만 주입).
- **S3 Vectors**: ai 워크로드의 벡터 검색 저장소. `AI_VECTOR_BUCKET`/`AI_VECTOR_INDEX`로 설정되며 Pod 기동 시 `GetIndex` 검증을 통과해야 함(차원·거리척도·비필터키 불일치 시 기동 실패).
- **AWS Bedrock**: LLM 호출 대상 (`AWS_BEDROCK_REGION`). 인덱스와 임베딩 모델은 짝을 이루므로 ConfigMap에 노출하지 않음(변경 시 전건 재임베딩 필요).
- **AWS Cognito**: 전 서비스 공통으로 `AWS_COGNITO_REGION`, `AWS_COGNITO_USER_POOL_ID`를 ConfigMap에서, Client ID/Secret은 `member-secret`에서만 주입(인증을 담당하는 member 서비스만 Client 자격증명 보유).

---

## 7. NetworkPolicy (`k8s/base/09-networkpolicy.yaml`)

| Policy | 대상 Pod | Ingress 허용 | Egress 허용 |
|---|---|---|---|
| `lion-app-baseline` | `tier: backend` 전체 | `ingress-nginx`/`kube-system` 네임스페이스, `${VPC_CIDR}`(kubelet probe용 노드 IP 대역), 동일 네임스페이스 `tier: backend` — 전부 TCP 8080 | CoreDNS(`kube-system`, UDP/TCP 53), 동일 네임스페이스 `tier: backend`(TCP 8080), Aurora(TCP 5432)+Redis(TCP 6379), S3/ECR/Bedrock 등 외부 HTTPS(TCP 443) |
| `order-internal-allowlist` | `app: order-service` | `app: catalog-service` Pod, `ingress-nginx` 네임스페이스, `${VPC_CIDR}` — TCP 8080 | (미지정, baseline 상속) |

- `order-internal-allowlist`는 order-service의 `/internal/**` 재고 API를 catalog-service만 호출 가능하도록 하는 2차 방어선(1차는 §4의 Ingress 경로 미등록).
- kubelet의 헬스체크 트래픽은 Pod가 아닌 노드 IP에서 발신되므로 `namespaceSelector`로 매칭되지 않아 `ipBlock: ${VPC_CIDR}`를 별도로 열어야 함 — 누락 시 정책 강제 CNI 환경에서 전 Pod가 동시에 `NotReady`로 전환되는 장애 유발 가능.
- NetworkPolicy는 CNI가 지원해야 유효(EKS 기본 VPC CNI는 최신 버전에서 지원); 미지원 클러스터에서는 리소스가 생성돼도 무효과.

---

## 8. 확인된 설계 결정 요약 (매니페스트 주석 근거)

1. **Aurora 단일 클러스터, ai_db 별도 클러스터 분리는 폐기** — pgvector 불요(벡터는 S3 Vectors로 이관), Serverless v2 auto-pause만으로는 별도 클러스터 비용 정당화 불가.
2. **order만 RDS Proxy 연결** — ai_db의 Serverless v2 auto-pause와 상충하기 때문에 order 전용.
3. **API Gateway 미도입** — ALB Ingress 경로 라우팅으로 충분, 게이트웨이 추가는 SPOF 증가로 판단.
4. **ai-rag/ai-bot 워크로드 분리는 Service/Deployment 레벨, 마이크로서비스 자체 분리는 아님** — 이미지 동일, 리소스/HPA/메트릭만 분리. 서비스를 온전히 쪼개는 것보다 약 10배 저비용으로 자원 격리 달성.

---

## 9. 문서 저장 결과

`docs/k8s-spec.md` 로 저장 완료 (본 파일). `docs/` 디렉터리는 이번 작업으로 신규 생성됨(기존에 존재하지 않았음).
