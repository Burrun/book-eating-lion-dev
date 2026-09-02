-- 서비스 계정 4개 + 스키마 4개 + 권한.
--
-- 실행 주체: 마스터 계정(bookadmin). RDS가 프라이빗 서브넷에 있어 CI 러너가 붙을 수
-- 없으므로 VPC 안(dev EC2 점프박스 또는 k8s Job)에서 돌린다.
--
--   psql "host=<rds-endpoint> user=bookadmin dbname=bookdb_prod sslmode=require" \
--        -v ON_ERROR_STOP=1 \
--        -v catalog_pw="$CATALOG_PW" -v order_pw="$ORDER_PW" \
--        -v member_pw="$MEMBER_PW"  -v ai_pw="$AI_PW" \
--        -f db/postgres/00-init.sql
--
-- 비밀번호 값은 Terraform db_service_accounts 모듈이 Secrets Manager에 넣은 것을
-- 그대로 써야 한다. 값이 어긋나면 파드가 인증 실패로 CrashLoop 한다.
--
-- 테이블은 여기서 만들지 않는다. 각 앱이 기동하면서 Liquibase
-- (db.changelog-master.yaml)로 자기 스키마에 만든다. 그래서 각 계정이 자기 스키마의
-- OWNER 여야 한다 - USAGE 만 주면 Liquibase 의 CREATE TABLE 이 첫 기동에서 죽는다.
--
-- 멱등하다. 반복 실행해도 안전하다.

\set ON_ERROR_STOP on

-- ── 롤 ──────────────────────────────────────────────────────────────
-- CREATE ROLE 은 IF NOT EXISTS 를 지원하지 않아 DO 블록으로 감싼다.
-- 이미 있으면 비밀번호만 맞춰준다(시크릿 로테이션 후 재실행 대응).
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catalog_svc') THEN
    CREATE ROLE catalog_svc LOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_svc') THEN
    CREATE ROLE order_svc LOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'member_svc') THEN
    CREATE ROLE member_svc LOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_svc') THEN
    CREATE ROLE ai_svc LOGIN;
  END IF;
END
$$;

-- RDS PostgreSQL 16에서는 superuser가 아니므로 스키마 생성/소유권 변경(AUTHORIZATION/OWNER TO)을
-- 수행하려면 bookadmin에게 해당 서비스 롤의 멤버십(SET ROLE 권한)이 있어야 한다.
GRANT catalog_svc, order_svc, member_svc, ai_svc TO bookadmin;

ALTER ROLE catalog_svc PASSWORD :'catalog_pw';
ALTER ROLE order_svc   PASSWORD :'order_pw';
ALTER ROLE member_svc  PASSWORD :'member_pw';
ALTER ROLE ai_svc      PASSWORD :'ai_pw';

-- ── 스키마 (소유자 = 해당 서비스 계정) ──────────────────────────────
CREATE SCHEMA IF NOT EXISTS catalog_db AUTHORIZATION catalog_svc;
CREATE SCHEMA IF NOT EXISTS order_db   AUTHORIZATION order_svc;
CREATE SCHEMA IF NOT EXISTS member_db  AUTHORIZATION member_svc;
CREATE SCHEMA IF NOT EXISTS ai_db      AUTHORIZATION ai_svc;

-- 이미 존재하던 스키마(EC2에서 마스터가 만든 것)를 이관한 경우 소유자를 넘긴다.
ALTER SCHEMA catalog_db OWNER TO catalog_svc;
ALTER SCHEMA order_db   OWNER TO order_svc;
ALTER SCHEMA member_db  OWNER TO member_svc;
ALTER SCHEMA ai_db      OWNER TO ai_svc;

-- ── search_path ─────────────────────────────────────────────────────
-- JDBC URL 에 currentSchema 가 이미 있지만, psql 로 직접 붙어 확인할 때도 같은
-- 스키마가 잡히도록 롤 기본값을 맞춰 둔다.
ALTER ROLE catalog_svc SET search_path = catalog_db;
ALTER ROLE order_svc   SET search_path = order_db;
ALTER ROLE member_svc  SET search_path = member_db;
ALTER ROLE ai_svc      SET search_path = ai_db;

-- ── 경계: 남의 스키마에 접근할 수 없어야 한다 ───────────────────────
-- PUBLIC 롤에서 CONNECT 를 회수하지는 않는다(마스터 운영 작업이 막힌다).
-- 스키마 단위 권한만으로 경계를 만든다 - 각 계정은 자기 스키마의 OWNER 이고,
-- 남의 스키마에는 아무 GRANT 도 하지 않으므로 접근 시 permission denied 가 난다.
--
-- 검증:
--   psql -U catalog_svc -c "SELECT * FROM order_db.inventory;"
--   -> ERROR: permission denied for schema order_db
--
-- public 스키마에 테이블을 만들지 못하게 막는다(PostgreSQL 15+ 는 기본으로
-- 막혀 있지만 명시해 둔다 - 스키마 경계를 우회하는 공용 테이블 방지).
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
