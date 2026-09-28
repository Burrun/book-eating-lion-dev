# 로컬 실행

[프로젝트 소개](../README.md)

저장소 루트에서 시작한다. 아래 명령은 Nushell 기준이며, 서버 실행은 각각 별도 터미널에서 진행한다.

> 기존 README에 기록된 2026-09-27 재현 환경은 클린 환경(PostgreSQL 16.13 · Redis 7.0 · JDK 21)이다. 당시 `catalog`/`order`/`member` 기동과 아래 검증 항목을 확인한 것으로 기록돼 있다. `ai-service`는 AWS 자격증명이 필요하다([아래](#ai-service-는-aws-가-필요하다)).

## 준비물

- JDK 21
- PostgreSQL 16, Redis 7 (또는 Docker)
- `psql` 클라이언트

## 1. PostgreSQL · Redis 띄우기

Docker를 쓰는 경우(로컬에 설치된 PostgreSQL 16 / Redis 7을 그대로 써도 된다):

```nu
docker run -d --name lion-pg    -p 5432:5432 -e POSTGRES_PASSWORD=rootpassword -e POSTGRES_DB=bookdb postgres:16
docker run -d --name lion-redis -p 6379:6379 redis:7
```

## 2. 스키마 4개 · 서비스 계정 4개 만들기

`00-init.sql`은 운영(RDS)의 마스터 계정 `bookadmin`을 전제로 한다. 로컬에는 그 롤이 없어서 **먼저 만들어야 한다**(없으면 `role "bookadmin" does not exist`로 멈춘다).

```nu
$env.PGHOST = 'localhost'
$env.PGUSER = 'postgres'
$env.PGPASSWORD = 'rootpassword'

psql -d bookdb -c "CREATE ROLE bookadmin LOGIN SUPERUSER"   # 로컬 전용

psql -d bookdb -v ON_ERROR_STOP=1 -v catalog_pw=catalog_pw -v order_pw=order_pw -v member_pw=member_pw   -v ai_pw=ai_pw -f db/postgres/00-init.sql
```

비밀번호 값은 각 서비스의 `application-local.yml`과 같아야 한다. 테이블은 여기서 만들지 않는다 — 각 서비스가 기동하면서 **Liquibase**로 자기 스키마에 만든다.

## 3. 빌드 · 기동

각 터미널에서 저장소 루트의 `backend`로 이동한 뒤 실행한다. Windows에서는 아래 `./gradlew` 대신 `./gradlew.bat`을 사용한다.

```nu
cd backend
./gradlew :apps:catalog-api:bootJar :apps:order-api:bootJar :apps:member-api:bootJar :apps:ai-api:bootJar

# 터미널 3개 (기본 프로파일 = local)
with-env {KAKAOPAY_SECRET_KEY: dummy} { java -jar apps/order-api/build/libs/order-api-0.0.1-SNAPSHOT.jar }     # :8082
java -jar apps/member-api/build/libs/member-api-0.0.1-SNAPSHOT.jar                            # :8083
java -jar apps/catalog-api/build/libs/catalog-api-0.0.1-SNAPSHOT.jar                          # :8081
```

`KAKAOPAY_SECRET_KEY`는 기본값이 없어서 비워 두면 order가 뜨지 않는다(결제 승인을 실제로 호출하지 않으면 아무 값이나 된다).

## 4. 데모 데이터 넣기

새 터미널을 저장소 루트에서 열고, 2단계의 PostgreSQL 환경변수를 설정한 뒤 실행한다.

```nu
psql -d bookdb -v ON_ERROR_STOP=1 -f db/postgres/90-demo-data.sql   # 도서 25권 + 재고 + FAQ 등
```

> **모든 서비스가 한 번 기동해 Liquibase가 끝난 뒤에** 넣는다. 이 파일에는 `CREATE TABLE IF NOT EXISTS`가 있어서, 어떤 서비스보다 먼저 돌면 그 서비스의 테이블을 엔티티와 다른 모양으로 먼저 만들어 버린다(실제로 `ai_db.lions.member_id`가 `BIGINT`로 생겨 ai-service가 `Schema-validation: wrong column type`으로 기동 실패했다).

## 5. 동작 확인

```nu
# 헬스체크
curl localhost:8081/actuator/health   # {"status":"UP"}
curl localhost:8082/actuator/health
curl localhost:8083/actuator/health

# 재고 조합: 도서 정보(catalog) + 재고(order, Feign 벌크 조회)
curl localhost:8081/api/catalog/books/1          # "stockQuantity": 999

# 스키마 경계: 남의 스키마는 권한으로 막힌다
with-env {PGPASSWORD: catalog_pw} { psql -U catalog_svc -d bookdb -c "SELECT * FROM order_db.inventory" }
# ERROR:  permission denied for schema order_db

# 장애 격리: order 를 내려도 서점은 돈다
#   (order 프로세스 종료 후)
curl localhost:8081/api/catalog/books/1          # HTTP 200, "stockQuantity": -1
```

`stockQuantity: -1`은 "재고 조회 실패"다. 품절(`0`)과 구분하려고 음수를 쓴다 — 프론트는 재고 영역만 degrade하고 도서 정보는 정상 노출한다. 이 절차로 주문 서비스 장애 시 도서 조회 응답을 확인할 수 있다.

Swagger UI는 로컬 프로파일에서만 켜진다: `http://localhost:{8081|8082|8083}/swagger-ui.html`

## ai-service 는 AWS 가 필요하다

아래 기동 명령은 `backend` 디렉터리에서 실행한다.

`ai-service`는 기동 시 `VectorIndexVerifier`가 S3 Vectors 인덱스를 조회하므로, 자격증명 없이 띄우면 `S3Vectors … AccessDeniedException(403)`으로 멈춘다(Liquibase까지는 성공한다). Bedrock/S3 Vectors 권한이 있는 AWS 프로파일 또는 자격증명을 먼저 설정하고 실행한다.

```nu
with-env {AWS_REGION: ap-northeast-2 AI_VECTOR_BUCKET: "<벡터 버킷>"} { java -jar apps/ai-api/build/libs/ai-api-0.0.1-SNAPSHOT.jar }
```

| 로컬에서 되는 것 | 로컬에서 안 되는 것 (AWS 연동 환경에서 검증) |
| --- | --- |
| catalog / order / member 기동, Redis Streams 이벤트, 분산 락, Feign fallback | Bedrock(LLM·임베딩), S3 Vectors, SQS 파이프라인, Cognito 로그인 |

- SQS 리스너는 `SQS_INGEST_ENABLED` / `SQS_PURCHASE_ENABLED`가 기본 `false`다. 로컬에서 켜면 배포된 컨슈머와 **실제 이벤트를 경쟁**해 가져가므로 켜지 않는다.
- 환경변수 전체 목록은 [`.env.example`](../.env.example) 참고.

## 테스트 · 포맷

저장소 루트에서 실행한다.

```nu
cd backend
./gradlew spotlessCheck   # CI 와 같은 포맷 검사 (spotlessApply 로 자동 수정)
./gradlew test
```

부하 테스트(k6)는 [`k6/README.md`](../k6/README.md), API 수동 호출은 [`postman/README.md`](../postman/README.md) 참고.
