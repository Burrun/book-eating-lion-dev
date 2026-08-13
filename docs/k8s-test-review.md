# `k8s/test` 인클러스터 테스트 DB 환경 리뷰

> 스캔 대상: `k8s/test/*`(전체 10개 매니페스트), `k8s/base/*`, `k8s/order/*`, `k8s/catalog/*`, `k8s/member/*`, `k8s/ai/*`, `backend/apps/*/src/main/resources/application*.yml`, `.github/workflows/main-cd.yml`, `db/postgres/00-init.sql`
> 기준 브랜치: `infr/k8s-test` · 문서 작성: 2026-08-12 · **갱신: 2026-08-13**
> 3회에 걸쳐 진행된 검증을 통합했다 — 1차/2차 코드 리뷰(§2, §3), VM에서의 실제 기동·통신 테스트(§4), `/code-review`로 범위를 `k8s/test` 밖(운영 매니페스트)까지 넓힌 3차 리뷰(§5). 각 항목은 실제 매니페스트/애플리케이션 소스 코드 대조 또는 실기동 결과로 검증된 것만 기재한다.

---

## 1. 요약

| 구분 | 건수 | 상태 |
|---|---|---|
| 1차 리뷰(§2) | 4건 | ✅ Fixed / ⚠️ Accepted(구현됨, 아래 §2.3 참고) |
| 2차 리뷰(§3) | 3건 | ✅ Fixed |
| VM 실기동 검증(§4) | catalog/member 목업 + DB + Redis 통신 | ✅ 확인 완료(order-service 컨테이너 자체는 노드 이미지 배포 문제로 미검증, 설정 문제 아님) |
| 3차 리뷰 — `k8s/test` 밖 운영 매니페스트(§5) | 7건 | ✅ Fixed |

**결론: `k8s/test` 자체는 더 이상 알려진 차단 요인이 없다.** 이번 세션 중 실제 VM에서 `postgres-test`/`redis-test`/`catalog-mock`/`member-mock`을 기동하고, order-service가 실제로 호출할 경로(`/api/books/{id}`, `/internal/cards/{id}/deduct` 등)로 직접 curl해 DNS/TCP/HTTP/스키마 검증까지 확인했다(§4). order-service 컨테이너 자체의 기동은 멀티 노드 클러스터에서 이미지가 스케줄된 노드에 없어 확인하지 못했는데, 이는 `k8s/test` 설정 문제가 아니라 순수 이미지 배포(운영) 문제다.

부수적으로, `k8s/test`를 실제로 구동해보는 과정에서 **`k8s/test` 밖 운영 매니페스트에도 동일 계열의 결함이 있다는 것**이 드러나 §5에서 함께 정리·수정했다 — `application-prod.yml`에 JWT issuer-uri가 4개 서비스 전부 빠져 있어 지금 상태로는 EKS 배포 시에도 기동 실패했을 것이다.

---

## 2. 1차 리뷰 — 조치 결과

| # | 파일 | 내용 | 조치 |
|---|---|---|---|
| 1 | `02-test-config.yaml` | ConfigMap이 `DB_HOST/PORT/NAME`만 오버라이드하고 `DB_USERNAME/PASSWORD`는 운영 Secret(`order-secret` 등)에서 그대로 읽어 테스트 DB 인증 실패 | ✅ `03-test-secret.yaml`(`test-db-secret`, `lion-app`) 신설로 해결 |
| 2 | `01-test-postgres.yaml` | `POSTGRES_USER/PASSWORD`가 매니페스트에 평문 하드코딩 — 저장소 전역 컨벤션(자격증명은 항상 Secret, envsubst 치환)과 불일치 | ✅ `postgres-test-secret`(`lion-db`)을 만들어 `secretKeyRef`로 참조하도록 변경 |
| 3 | `01-test-postgres.yaml` | 단일 슈퍼유저(`test_user`) 계정 하나뿐이라, 운영이 실제 격리 경계로 삼는 "서비스별 DB 계정 권한"(`db/postgres/00-init.sql`의 `member_svc/catalog_svc/order_svc/ai_svc` 분리)을 재현하지 못함 | ✅ **§4(아래) 권고대로 실제 구현함.** `04-test-db-init.yaml`이 `db/postgres/00-init.sql`을 그대로 재사용해 스키마 부트스트랩(§3.2)과 동시에 서비스별 Role 분리까지 한 번에 해결했다. `test-db-secret`도 `order_svc`/`order_pw`로 전환(§5.3 참고) |
| 4 | `k8s/test/test-db.yaml`, `test-comfigmap.yaml` | 0바이트 잔여 스텁 파일, 오탈자(`comfigmap`) 포함 | ✅ 사용자가 직접 삭제 완료 |

---

## 3. 2차 리뷰 — 조치 결과

### 3.1 `SPRING_PROFILES_ACTIVE=test` — 대응하는 프로파일 파일 자체가 없음

- **근거**: `application-test.yml`은 어떤 서비스에도 없고, datasource 설정은 `application-prod.yml`에만 있다.
- ✅ **조치**: `k8s/test/02-test-config.yaml`에서 `SPRING_PROFILES_ACTIVE: "prod"`로 설정. 이 저장소에서 `prod` 프로파일은 "환경변수 주입 기반 설정"을 뜻하지 실제 운영 여부를 뜻하지 않는다.

### 3.2 테스트 Postgres에 스키마 부트스트랩이 없음

- **근거**: 각 서비스 JDBC URL이 `currentSchema=order_db`(catalog는 `catalog_db` 등)를 하드코딩하는데, 대응하는 스키마 생성 절차가 `postgres-test` Pod에 없었다.
- ✅ **조치**: `k8s/test/04-test-db-init.yaml`(ConfigMap `postgres-test-init`)이 `db/postgres/00-init.sql`을 그대로 담아 `/docker-entrypoint-initdb.d/00-init.sql`로 마운트(`01-test-postgres.yaml`). 원본과 수동 동기화해야 하는 한계는 있음(§6 참고).

### 3.3 프로브가 Secret 대신 하드코딩된 사용자명을 참조

- ✅ **조치**: `01-test-postgres.yaml`의 `readinessProbe`/`livenessProbe`를 `["sh", "-c", "pg_isready -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\""]`로 변경해 Secret에서 주입된 컨테이너 환경변수를 그대로 참조하도록 수정.

---

## 4. VM 실기동 검증 (2026-08-13)

목적은 "EKS에서 실제로 잘 되는가"가 아니라 **MSA 구조 간 통신이 설정대로 맞물리는가** 확인이었다(Aurora/Cognito/Bedrock 등 실제 AWS 리소스 연동은 VM 테스트로 증명할 수 없는 영역 — 별도 스테이징 환경이 필요하다).

- `postgres-test`, `redis-test`, `catalog-mock`(Prism, `08-test-catalog-mock.yaml`), `member-mock`(Prism, `09-test-member-mock.yaml`) 전부 `Running`.
- catalog-service의 실제 호출 경로(`CatalogClient.java`: `GET /api/books/{bookId}`)와 계약(`backend/contracts/catalog-v1.yaml`: `GET /api/catalog/books/{bookId}`)이 어긋나 있는 것을 발견 — `catalog-mock`에 nginx 경로 rewrite 브릿지를 얹어 우회. **계약과 구현의 경로 대조 미비**는 `backend/contracts/README.md`가 스스로 "아직 하지 않은 것"으로 꼽은 항목이 실제로 걸린 사례다.
- 임시 디버그 Pod(`nicolaka/netshoot`)에서 order-service가 실제로 호출할 경로로 직접 확인:
  - `GET catalog-service-mock:8080/api/books/1` → `200`, 계약 스키마와 일치하는 JSON
  - `POST member-service-mock:8080/internal/cards/1/deduct` → `200`, 계약 스키마와 일치
  - `postgres-test-service:5432`, `redis-test-service:6379` TCP 연결 성공
  - Prism 로그에 `VALIDATOR: passed the validation rules` 확인 — 단순 연결이 아니라 요청 스키마 자체가 계약과 맞다는 것까지 검증됨
- order-service 컨테이너 자체는 4노드(`master`/`db`/`node1`/`node2`) 클러스터에서 이미지를 `master`에만 로드해 다른 노드로 스케줄되며 `ImagePullBackOff` — **k8s/test 설정 문제가 아니라 순수 이미지 배포 문제**(실제 EKS는 ECR 중앙 레지스트리를 쓰므로 이 문제 자체가 없음). 이 시점에서 "MSA 통신 확인"이라는 원래 목적은 달성된 것으로 보고 중단.

---

## 5. 3차 리뷰 — `k8s/test` 밖 운영 매니페스트 (2026-08-13, `/code-review` 활용)

`k8s/test`를 실제로 구동해보며 발견한 결함 중 다수가 테스트 전용이 아니라 **운영 코드 자체의 결함**이라는 것이 드러나, `k8s/test`를 제외하고 `k8s/base`, `k8s/order`, `k8s/catalog`, `k8s/member`, `k8s/ai`를 대상으로 별도 리뷰를 진행했다.

| # | 대상 | 문제 | 조치 |
|---|---|---|---|
| 5.1 | order/catalog/member/ai `application-prod.yml` 전부 | `spring.security.oauth2.resourceserver.jwt.issuer-uri`가 4개 서비스 어디에도 없음. `SecurityConfig`가 전부 `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`를 무조건 호출해 `JwtDecoder` 빈을 못 만들면 컨텍스트 기동이 실패한다. `application-local.yml`에는 이미 있던 줄이 prod로 옮기며 빠짐 | ✅ 4개 파일에 `issuer-uri: https://cognito-idp.${AWS_COGNITO_REGION}.amazonaws.com/${AWS_COGNITO_USER_POOL_ID}` 추가(값은 이미 각 ConfigMap에 있던 것 재사용) |
| 5.2 | `order-api application-prod.yml` | `RedissonConfig`(재고 차감 분산 락)가 `spring.data.redis.host/port`를 기본값 없이 요구하는데 이 키가 없음. `REDIS_HOST` ConfigMap 키는 있었지만 이름이 달라 안 읽힘 | ✅ `spring.data.redis.host: ${REDIS_HOST}` / `port: 6379` 추가 |
| 5.3 | `k8s/base/03-secret.yaml`, `.github/workflows/main-cd.yml` | `kakaopay.secret-key: ${KAKAOPAY_SECRET_KEY}`(기본값 없음, 모든 프로파일 공통)가 어느 매니페스트/CI에도 배선돼 있지 않음 — 플레이스홀더조차 없어 다른 시크릿과 컨벤션이 다름 | ✅ `order-secret`에 `KAKAOPAY_SECRET_KEY: "${KAKAOPAY_SECRET_KEY}"` 추가, `main-cd.yml`의 `base` job env/필수값체크/envsubst VARS에 배선. **GitHub Secrets에 실제 값 등록 필요**(Terraform 대상 아님 — 제3자 API 키) |
| 5.4 | `catalog-api application-prod.yml` | 5.2와 동일한 원인(`REDIS_HOST` 이름 불일치)으로 Redis 연결 실패 — 구매 후 리뷰 작성 권한 이벤트(Redis Streams)를 영원히 못 받음 | ✅ `spring.data.redis.host/port` 추가 |
| 5.5 | `ai-api application-prod.yml` | 동일 원인 — `DailyQuota`는 fail-open이라 일일 사용량 제한이 조용히 무력화되고, `FedBookCache`는 항상 미스라 DB 부하 추가 | ✅ `spring.data.redis.host/port` 추가 |
| 5.6 | `k8s/base/09-networkpolicy.yaml` | `order-internal-allowlist`("`/internal/**`은 catalog-service만" 의도)가 `lion-app-baseline`의 더 넓은 규칙(같은 네임스페이스 backend 전체 + VPC 전체, 8080)에 의해 실질적으로 무력화됨 — NetworkPolicy는 같은 Pod에 여러 정책이 걸리면 합집합(OR)으로 적용되기 때문 | ✅ **주석만 정직하게 갱신**(옵션 A로 진행 — 정책 구조 변경은 보류). 실제로 좁히려면 `lion-app-baseline`의 동일 네임스페이스 인그레스 규칙에서 order-service를 제외하는 구조 변경이 필요(별도 판단) |
| 5.7 | `k8s/ai/deployment-bot.yaml`, `deployment-rag.yaml` | order/catalog/member엔 있는 `podAntiAffinity`(노드 분산)가 ai에만 빠짐 — 노드 하나에 replica가 몰릴 수 있음 | ✅ 동일 패턴의 `podAntiAffinity` 추가 |

---

## 6. 남은 참고 사항 (우선순위 낮음)

- **`k8s/test/04-test-db-init.yaml`**은 `db/postgres/00-init.sql`을 ConfigMap 리터럴로 복사한 것이라 원본이 바뀌면 수동으로 동기화해야 한다(`/code-review` 지적). `catalog-contract`/`member-contract`(§4, `backend/contracts/*.yaml`)는 같은 문제를 피하려고 `kubectl create configmap --from-file`로 원본을 직접 참조하게 만들었는데, 이 파일만 그 원칙을 못 지켰다 — 필요 시 같은 방식으로 전환 권장.
- **`k8s/order/hpa.yaml`과 `k8s/test/06-test-order-deployment.yaml`의 이름 충돌**은 후자를 `order-test-deployment`로 이름을 바꿔 근본적으로 해결했다(운영 HPA를 실수로 같이 적용해도 더 이상 테스트 Deployment를 건드리지 않음).
- **§5.6 NetworkPolicy 구조 변경**(옵션 B, `lion-app-baseline`에서 order-service 제외)은 보안 강화 효과가 있지만 CNI가 NetworkPolicy를 강제하는 클러스터에서 실제 트래픽에 영향을 줄 수 있어 별도로 신중히 진행할 것.

---

## 7. 체크리스트

- [x] `02-test-config.yaml`: `SPRING_PROFILES_ACTIVE` → `"prod"`
- [x] `01-test-postgres.yaml`: 스키마 초기화 ConfigMap + `/docker-entrypoint-initdb.d` 마운트
- [x] `01-test-postgres.yaml`: 프로브 커맨드가 `POSTGRES_USER`/`POSTGRES_DB` 환경변수를 참조
- [x] 서비스별 Role 분리(`order_svc` 등) — `00-init.sql` 재사용으로 해결
- [x] order/catalog/member/ai `application-prod.yml`: JWT issuer-uri 추가
- [x] order-api, catalog-api, ai-api: `spring.data.redis.host/port` 추가
- [x] `KAKAOPAY_SECRET_KEY` 배선(매니페스트 + CI) — **GitHub Secrets 실제 값 등록은 별도**
- [x] ai-bot/ai-rag: `podAntiAffinity` 추가
- [x] NetworkPolicy 주석 정직화
- [ ] (선택, 보류) NetworkPolicy 구조 변경으로 `/internal/**` 실제 제한
- [ ] (선택, 낮은 우선순위) `04-test-db-init.yaml`을 `--from-file` 방식으로 전환
