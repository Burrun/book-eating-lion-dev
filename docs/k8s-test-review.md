# `k8s/test` 인클러스터 테스트 DB 환경 리뷰

> 스캔 대상: `k8s/test/01-test-postgres.yaml`, `02-test-config.yaml`, `03-test-secret.yaml`
> 대조 자료: `k8s/base/*`, `k8s/order/*`, `backend/apps/*/src/main/resources/application*.yml`, `db/postgres/00-init.sql`
> 기준 브랜치/커밋: `feat/order-domain-completion` (`1b036e4`) · 문서 작성: 2026-08-12
> 2회에 걸쳐 진행된 코드 리뷰(1차: 최초 작성본, 2차: 1차 수정 반영 후) 결과를 통합했다. 각 항목은 실제 매니페스트/애플리케이션 소스 코드 대조로 검증된 것만 기재한다.

---

## 1. 요약

| 구분 | 건수 | 상태 |
|---|---|---|
| 1차 리뷰에서 발견 · 이번 세션에서 수정 완료 | 3건 | ✅ Fixed |
| 1차 리뷰에서 발견 · 의도적으로 보류(설계 트레이드오프) | 1건 | ⚠️ Accepted |
| 2차 리뷰(수정 반영 후)에서 신규 발견 · **미수정** | 3건 | 🔴 Open |

**결론: 현재 `k8s/test` 상태로 `kubectl apply -f ./k8s/test` 후 애플리케이션을 테스트 DB에 붙이면, `SPRING_PROFILES_ACTIVE` 및 스키마 부트스트랩 문제로 인해 여전히 기동에 실패한다.** 아래 §3 Open 항목을 해결해야 실사용 가능하다.

---

## 2. 1차 리뷰 — 조치 결과

| # | 파일 | 내용 | 조치 |
|---|---|---|---|
| 1 | `02-test-config.yaml` | ConfigMap이 `DB_HOST/PORT/NAME`만 오버라이드하고 `DB_USERNAME/PASSWORD`는 운영 Secret(`order-secret` 등)에서 그대로 읽어 테스트 DB 인증 실패 | ✅ `03-test-secret.yaml`(`test-db-secret`, `lion-app`) 신설로 해결 |
| 2 | `01-test-postgres.yaml` | `POSTGRES_USER/PASSWORD`가 매니페스트에 평문 하드코딩 — 저장소 전역 컨벤션(자격증명은 항상 Secret, envsubst 치환)과 불일치 | ✅ `postgres-test-secret`(`lion-db`)을 만들어 `secretKeyRef`로 참조하도록 변경 |
| 3 | `01-test-postgres.yaml` | 단일 슈퍼유저(`test_user`) 계정 하나뿐이라, 운영이 실제 격리 경계로 삼는 "서비스별 DB 계정 권한"(`db/postgres/00-init.sql`의 `member_svc/catalog_svc/order_svc/ai_svc` 분리)을 재현하지 못함 → 교차 서비스 권한 버그를 이 테스트 DB로는 잡을 수 없음 | ⚠️ **의도적 보류.** 단일 Pod 스탠드얼론 테스트 DB의 목적(빠른 기동·연결 확인)과 상충하는 범위 확장이라 별도 판단 필요. 아래 §4 권고 참고 |
| 4 | `k8s/test/test-db.yaml`, `test-comfigmap.yaml` | 0바이트 잔여 스텁 파일, 오탈자(`comfigmap`) 포함 | ✅ 사용자가 직접 삭제 완료 |

---

## 3. 2차 리뷰 — 신규 발견 (미수정, 🔴 Open)

### 3.1 `SPRING_PROFILES_ACTIVE=test` — 대응하는 프로파일 파일 자체가 없음

- **위치**: `k8s/test/02-test-config.yaml:21`
- **근거**: `backend/apps/*/src/main/resources/`에는 `application.yml`(base) · `application-local.yml` · `application-prod.yml` 세 개만 존재한다. `application-test.yml`은 어떤 서비스에도 없다.
- **base `application.yml`에는 `spring.datasource.*` 블록이 없다** (order-api 기준 확인, `spring.profiles.active: local` 기본값만 존재).
- **datasource 설정은 오직 `application-prod.yml`에만 있고**, 여기서 `${DB_HOST}/${DB_PORT}/${DB_NAME}/${DB_USERNAME}/${DB_PASSWORD}`를 그대로 참조한다 — 즉 이번 세션에서 만든 ConfigMap/Secret 오버라이드는 사실 `prod` 프로파일을 겨냥해 설계된 것과 동일한 키 이름이다.
- **실패 시나리오**: `SPRING_PROFILES_ACTIVE=test`인 채로 기동하면 Spring이 `application-test.yml`을 찾지 못해 조용히 무시하고, base `application.yml`에는 datasource 설정이 없으므로 DataSource 자체가 구성되지 않아 기동 실패한다. 이번 세션에서 만든 override 체인(ConfigMap+Secret)이 아예 검증되지 않는 상태다.
- **권고 수정**: `SPRING_PROFILES_ACTIVE: "test"` → `"prod"`. 이름은 오해의 소지가 있지만, 이 저장소에서 `prod` 프로파일은 "환경변수 주입 기반 설정"을 뜻하는 프로파일이지 실제 운영 여부를 뜻하지 않는다.

### 3.2 테스트 Postgres에 스키마 부트스트랩이 없음

- **위치**: `k8s/test/01-test-postgres.yaml:32` (컨테이너 spec 전체 — 초기화 스크립트 마운트 없음)
- **근거**: 각 서비스 `application-prod.yml`의 JDBC URL이 `currentSchema=order_db`(catalog는 `catalog_db` 등)를 하드코딩한다. `db/postgres/00-init.sql`이 실제/로컬 DB에서 `CREATE SCHEMA member_db/catalog_db/order_db/ai_db` + 서비스별 Role을 만들어주는데, 이 초기화 스크립트에 대응하는 절차가 `postgres-test` Pod에는 없다.
- **실패 시나리오**: §3.1을 고쳐 `prod` 프로파일이 정상 로드되어도, `lion_test_db` 데이터베이스 안에 `order_db`라는 스키마가 없으므로 `schema "order_db" does not exist`로 실패한다. Hibernate `ddl-auto: update`는 스키마 내 테이블은 만들어도 스키마 자체는 만들지 않는다.
- **권고 수정**: `postgres:15-alpine`은 `/docker-entrypoint-initdb.d/*.sql`을 최초 기동 시 자동 실행한다. `db/postgres/00-init.sql`(또는 그 스키마 생성 부분만 축약한 버전)을 ConfigMap으로 만들어 해당 경로에 볼륨 마운트하면 해결된다.

### 3.3 프로브가 Secret 대신 하드코딩된 사용자명을 참조

- **위치**: `k8s/test/01-test-postgres.yaml:61` (`readinessProbe`), 동일 파일 liveness 부분
- **근거**: `pg_isready -U test_user -d lion_test_db`가 리터럴로 남아 있음. 반면 같은 파일 상단 주석(§환경변수 절)은 "평문 하드코딩을 피하고 값이 어긋날 여지를 없앤다"를 명시적 목표로 걸어 뒀는데, 프로브 커맨드는 그 목표를 벗어나 있다.
- **실패 시나리오**: `postgres-test-secret`의 `POSTGRES_USER`를 나중에 바꾸면 프로브만 구버전 사용자명을 계속 체크한다. `pg_isready`는 인증까지는 확인하지 않는 얕은 헬스체크라, 실제 계정 정보가 어긋나도 프로브는 계속 Ready를 보고해 드리프트를 은폐할 수 있다.
- **권고 수정**: `env`에서 `POSTGRES_USER`를 이미 주입받고 있으므로, `exec.command`를 셸 형태로 바꿔 `$POSTGRES_USER`를 참조(`["sh", "-c", "pg_isready -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\""]`)하거나, 최소한 `$(POSTGRES_USER)`/`$(POSTGRES_DB)` 형태의 Downward 참조로 정리.

---

## 4. §2.3(단일 슈퍼유저) 관련 권고 — 참고용, 우선순위 낮음

`test_user` 하나로 전체 DB 권한을 갖는 현재 구조는 "빠르게 뜨는 연결 테스트용 DB"라는 목적에는 충분하다. 다만 §3.2 스키마 부트스트랩을 어차피 손대야 하는 김에, `db/postgres/00-init.sql`을 그대로 재사용해 서비스별 Role(`order_svc`/`catalog_svc`/...)까지 복제하면 두 문제(스키마 부재 + 권한 경계 부재)를 한 번에 해소할 수 있다. 다만 그 경우 `03-test-secret.yaml`의 `DB_USERNAME/PASSWORD`도 서비스마다 다른 값(`order_svc/order_pw` 등)으로 나눠야 해서, 지금의 "값 하나로 전체 통일" 구조보다 파일이 다소 복잡해진다 — 트레이드오프이니 필요 시점에 판단.

---

## 5. 다음 액션

- [ ] `02-test-config.yaml`: `SPRING_PROFILES_ACTIVE` → `"prod"`
- [ ] `01-test-postgres.yaml`: 스키마 초기화 ConfigMap + `/docker-entrypoint-initdb.d` 마운트 추가
- [ ] `01-test-postgres.yaml`: 프로브 커맨드가 `POSTGRES_USER`/`POSTGRES_DB` 환경변수를 참조하도록 변경
- [ ] (선택) §4의 서비스별 Role 분리 여부 결정
