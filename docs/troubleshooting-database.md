# 트러블슈팅 — 데이터베이스 (PostgreSQL · 읽기/쓰기 분리 · 커넥션)

> PostgreSQL **클러스터 1개 / 스키마 4개 / 서비스 계정 4개** 구조와, catalog의 writer/reader 분리에서 겪은 문제들이다.
> 스키마의 원본은 각 앱의 Liquibase changelog(`backend/apps/*/src/main/resources/db/changelog/`)이고, 스키마·계정·권한은 `db/postgres/00-init.sql`이 만든다.

---

## 1. FK가 걸린 INSERT만 `permission denied` — 일반 쿼리는 멀쩡했다

**상태**: 해결 · `c97703a` (2026-08-27)

### 증상

- 도서 상세가 **"이미 본 책은 200, 처음 보는 책만 500"**
- 주문도 전부 실패

```text
ERROR: permission denied for schema catalog_db
QUERY: SELECT 1 FROM ONLY "catalog_db"."books" x WHERE "book_id" = $1 FOR KEY SHARE OF x
```

### 원인

PostgreSQL은 **FK 무결성 검사(RI trigger) 쿼리를 참조 대상 테이블 소유자 권한으로 강등해서** 실행한다. 테이블 소유자는 `*_svc` 계정인데, 그 계정에 자기 스키마의 `USAGE` 권한이 없었다. `00-init.sql`의 GRANT가 배포 DB에 **한 번도 적용된 적이 없었기** 때문이다.

그런데 당시 앱은 master(SUPERUSER)로 붙고 있어서 일반 SELECT/UPDATE는 전부 통과했다. **RI trigger에서만** 소유자 권한으로 내려가 터졌다.

- 도서 상세의 "최근 본 책" 기록: 처음 보는 책은 `recent_books` **INSERT**(FK 검사 O) → 500, 이미 본 책은 UPDATE(FK 컬럼 안 건드림, 검사 X) → 200
- 자식 테이블 13개가 같은 상태였고 `order_items` / `payments` / `member_coupons`가 포함돼 주문도 막혔다

### 해결

누락된 GRANT를 마이그레이션으로 적용했다. 이후 `00-init.sql`은 **각 계정이 자기 스키마의 OWNER**가 되도록 바뀌었다 — `USAGE`만 주면 Liquibase의 `CREATE TABLE`이 첫 기동에서 죽는다.

### 교훈

- **superuser로 테스트하면 권한 문제를 못 본다.** 로컬도 운영과 같은 서비스 계정(`catalog_svc` 등)으로 붙는다(`application-local.yml`).
- 권한 경계 확인은 한 줄이면 된다.

```bash
PGPASSWORD=catalog_pw psql -U catalog_svc -d bookdb -c "SELECT * FROM order_db.inventory"
# ERROR:  permission denied for schema order_db   ← 이게 정상
```

---

## 2. `inventory.version`이 NULL이라 결제가 전부 500

**상태**: 해결 · `c97703a` (2026-08-27)

### 증상

결제 500, `orders`가 계속 0행.

```text
NullPointerException: Cannot invoke "java.lang.Long.longValue()" because "current" is null
  at LongJavaType.next / Versioning.increment / getNextVersion
```

### 원인

`Inventory`는 `@Version`(낙관적 락)을 쓴다. 배포 DB의 테이블은 Hibernate가 만들어 `version` 컬럼에 **DEFAULT가 없었고**, 시드 INSERT는 `(book_id, stock)`만 채웠다. 시드 파일의 `CREATE TABLE … version NOT NULL DEFAULT 0`은 테이블이 이미 있어서 적용되지 않았다. `@Version`이 NULL이면 다음 버전을 계산하지 못해 재고 차감에서 터진다.

주문·주문항목 저장과 **이벤트 발행까지 끝난 뒤** 터져서 롤백됐다 — 이 사고가 [이벤트 발행 트러블슈팅 §1](troubleshooting-event-publishing.md#1-롤백된-주문에-리뷰-권한-ebook-열람-권한이-새어-나갈-수-있었다)의 배경이다.

### 해결

컬럼 정의 자체를 시드가 의도한 모양(`NOT NULL DEFAULT 0`)으로 맞췄다. 시드의 preflight 검사는 "NOT NULL인데 기본값이 없는 컬럼"을 보는데, 여기선 컬럼이 nullable이라 조건에서 빠져 못 잡았다.

검증: 도서 상세 14/14 200, 주문 생성 PAID (재고 999→998, version 0→1, 카드 사용액 0→27000, 장바구니 비움).

---

## 3. "재고가 부족합니다" — 실은 재고 행이 없었다

**상태**: 해결 · `24601d3`, `e0cfed3` (2026-08-26)

### 증상

데모 도서 21권이 카탈로그에는 보이는데 주문만 `재고가 부족합니다: bookId=7`로 실패.

### 원인

- `inventory`에 행이 4개(1, 101, 102, 9001)뿐이었다. 자동 채번된 데모 도서(2~22)에는 재고 행 자체가 없었다.
- `checkStock`이 **행 부재와 재고 부족을 같은 예외**로 던져서, 데이터 누락이 품절처럼 보였다.

### 해결

- 시드가 `book_id`를 하드코딩하지 않고 **카탈로그에서 읽어** 재고를 채운다(자동 채번 값은 환경마다 다르다).
- 행 부재는 `InventoryNotFoundException`(404)으로 분리했다. 둘은 운영이 손댈 곳이 다르다 — 전자는 시드/상품 등록 누락, 후자는 실제 품절.

---

## 4. 읽기/쓰기 분리가 "조용히" 무효화되는 조건들

**상태**: 설계 기록 · `23078d5` (2026-09-02, catalog read replica 활성화) · `RoutingDataSourceConfig`

catalog는 `@Transactional(readOnly = true)`면 reader(리드 리플리카), 아니면 writer(RDS Proxy)로 커넥션을 나눈다. 이 구조는 **틀려도 에러가 나지 않는** 함정이 여럿이다.

| 함정 | 결과 | 대응 |
| --- | --- | --- |
| `LazyConnectionDataSourceProxy`로 감싸지 않음 | Spring이 트랜잭션 시작 **전에** 커넥션을 잡아 readOnly 판단 시점이 지나감 → **전부 writer로** 감 (에러 없음) | 라우팅 DataSource를 `LazyConnectionDataSourceProxy`로 감싸 실제 쿼리 시점까지 커넥션 획득을 미룬다 |
| 표준 `spring.datasource`를 그대로 씀 | Boot 자동 구성이 DataSource를 하나 더 만들어 충돌 | `app.datasource.writer` / `app.datasource.reader`로 따로 바인딩 |
| 로컬에서도 라우팅을 켬 | writer/reader 설정이 없어 기동 실패 | `@Profile("prod")`에서만 활성화. 로컬은 DataSource 하나 |
| 리플리카를 **먼저** 붙이고 코드를 나중에 배포 | catalog의 쓰기와 기동 시 Liquibase가 **read-only 트랜잭션 에러**로 죽음 | 배포 순서: 라우팅 코드 배포 → 그다음 Terraform으로 `rds_read_replica_count = 1` |
| reader 풀을 크게 잡음 | reader는 **Proxy를 우회**해 리플리카에 직결 → `Pod 수 × 풀`이 `max_connections`를 그대로 압박 | writer 3 / reader 8 기본값, ConfigMap(`DB_WRITER_POOL_SIZE`/`DB_READER_POOL_SIZE`)으로 조절 |

Liquibase는 트랜잭션 밖에서 자기 커넥션을 관리해 `isCurrentTransactionReadOnly()`가 항상 `false` → 자동으로 writer로 간다.

### 확인 방법

```sql
-- 리플리카에서: catalog 조회 트래픽이 실제로 오는지
SELECT usename, application_name, state, count(*)
FROM pg_stat_activity WHERE usename = 'catalog_svc' GROUP BY 1,2,3;
```

---

## 5. dev와 prod가 같은 DB 엔드포인트 이름을 덮어썼다

**상태**: 해결 · `cc3fc0f` (2026-09-02)

### 원인

DB 엔드포인트를 감싸는 ExternalName Service(`db-primary-service`, `db-reader-service`)가 공용 네임스페이스 `lion-db` 하나에 있었다. dev와 prod가 같은 EKS 클러스터(integrated)를 쓰면서 둘 다 `k8s/base`를 apply하니, **나중에 배포한 환경의 엔드포인트가 앞 환경 것을 덮어썼다.** dev 파드가 prod DB를 가리킬 수 있는 상태였다.

### 해결

- `lion-db` 네임스페이스를 없애고 DB Service를 **각 환경 네임스페이스(`dev`/`prod`) 안에** 둔다. 앱은 `db-primary-service.${K8S_NAMESPACE}.svc.cluster.local`로 붙는다.
- `main-cd.yml`이 배포 직전에 **클러스터명·네임스페이스·DB 이름(`bookdb_dev`/`bookdb_prod`)이 대상 환경과 일치하는지** 검증하고, 다르면 멈춘다.

---

## 6. 커넥션 풀 3개로는 부하를 못 버텼다

**상태**: `dev` 브랜치 반영 (`59985cd`, PR #134, 2026-09-02) — `main` 미반영

### 증상

1차 부하 테스트에서 5xx가 **99.99%**. `max_connections`(당시 EC2 160)에는 도달조차 하지 않았다.

### 원인

전 서비스 HikariCP 풀이 3개인데 Tomcat 스레드는 200개다. 부하가 들어오자마자 풀이 고갈되고, `connection-timeout` 10초를 기다리다 실패했다. 풀 3과 10 모두 무너졌다.

### 해결

- order / member / ai는 이제 전부 `db-primary-service`(**RDS Proxy**) 경유다. Proxy가 앞단에서 멀티플렉싱하고 실제 DB 커넥션은 `MaxConnectionsPercent`가 제한하므로, 풀을 **12**로 올려도 DB(`db.t4g.micro`, `max_connections`≈110)가 안전하다.
- `connection-timeout` 10s → **3s**. 커넥션을 못 얻으면 빨리 실패시켜 서킷 브레이커가 제때 열리게 한다.
- catalog는 제외했다 — reader가 Proxy를 우회하므로(§4) 따로 판단해야 한다.

---

## 7. 로컬 재현에서 발견한 순서 문제 두 가지

**상태**: 문서화 (2026-09-27, README "실행 방법"에 반영)

README의 로컬 실행 절차를 클린 환경에서 그대로 돌려 보다가 발견했다.

### 7-1. `00-init.sql`이 `role "bookadmin" does not exist`로 멈춘다

`00-init.sql`은 RDS PostgreSQL 16의 마스터 계정 `bookadmin`을 전제로 `GRANT catalog_svc, … TO bookadmin`을 실행한다(RDS에서 마스터는 superuser가 아니라서, 스키마 소유권을 넘기려면 서비스 롤의 멤버여야 한다 — `245e2ab`). 로컬 PostgreSQL에는 이 롤이 없다.

→ 로컬에서는 먼저 `CREATE ROLE bookadmin LOGIN SUPERUSER`를 실행한다.

### 7-2. 데모 데이터를 너무 일찍 넣으면 ai-service가 뜨지 않는다

`90-demo-data.sql`에는 `CREATE TABLE IF NOT EXISTS`가 들어 있다. ai-service가 한 번도 뜨지 않은 상태(= Liquibase가 `ai_db` 테이블을 만들기 전)에 이 파일을 돌리면, 파일이 `ai_db.lions`를 **엔티티와 다른 모양**(`member_id BIGINT`)으로 먼저 만든다. 이후 ai-service는:

```text
Schema-validation: wrong column type encountered in column [member_id] in table [lions];
found [int8 (Types#BIGINT)], but expecting [varchar(255) (Types#VARCHAR)]
```

→ **4개 서비스가 모두 한 번 기동해 Liquibase가 끝난 뒤에** 데모 데이터를 넣는다. 이미 꼬였다면 해당 스키마를 지우고 다시 만든다.

```sql
DROP SCHEMA ai_db CASCADE;
CREATE SCHEMA ai_db AUTHORIZATION ai_svc;
```

---

## 참고 — 스키마 SQL 파일을 지운 이유

`db/postgres/01~04-*.sql`(목표 스키마)과 `docker-compose.yml`은 `5c264f9`(2026-08-27)에서 삭제했다.

- 적용된 적이 없었다. 배포 DB는 빈 스키마 4개만 만들고 테이블은 애플리케이션이 만들었다.
- 실 DB와 어긋나 있었다(DDL 38개 / 실 DB 34개 / 공통 31개). 그대로 적용하면 폐기된 테이블 7개를 새로 만들고 엔티티와 다른 컬럼을 심는다.
- 멱등하지 않았다(`CREATE TABLE` 39개 중 `IF NOT EXISTS` 0개).

지금은 **각 앱의 Liquibase changelog가 스키마의 원본**이고, 기동 시 자기 스키마에만 적용된다. 롤백 시 스키마는 되돌아가지 않는다는 점은 [README 롤백](../README.md#db는-롤백하지-않는다) 참고.
