-- =============================================================================
-- Aurora PostgreSQL 클러스터 A (provisioned) — 서점 본연
--   catalog_db / order_db / member_db 3개 스키마
--
-- MSA 전환 계획서 판단 ② — 물리 분리는 하지 않고 "스키마 + 계정 권한"까지만 간다.
-- 각 서비스 계정은 자기 스키마에만 USAGE 권한이 있으므로, 남의 테이블에 조인을
-- 걸면 컴파일 타임이 아니라 런타임에 즉시 권한 에러로 드러난다.
-- (Phase 1.5 통합 게이트의 "스키마 경계 위반" 검증 항목이 이걸 확인한다)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 스키마
-- ---------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS member_db;
CREATE SCHEMA IF NOT EXISTS catalog_db;
CREATE SCHEMA IF NOT EXISTS order_db;

-- ---------------------------------------------------------------------------
-- 서비스 계정
-- 운영에서는 Secrets Manager 가 주입한다. 아래 비밀번호는 로컬 전용이다.
-- ---------------------------------------------------------------------------
CREATE ROLE member_svc  LOGIN PASSWORD 'member_pw';
CREATE ROLE catalog_svc LOGIN PASSWORD 'catalog_pw';
CREATE ROLE order_svc   LOGIN PASSWORD 'order_pw';

-- ---------------------------------------------------------------------------
-- 경계 강제 — 기본 권한을 걷어내고 자기 스키마만 다시 준다.
--
-- PUBLIC 롤은 기본적으로 모든 스키마에 CREATE/USAGE 를 갖는다. 이걸 남겨두면
-- 계정을 나눈 의미가 없으므로 먼저 REVOKE 한다.
-- ---------------------------------------------------------------------------
REVOKE ALL ON SCHEMA public     FROM PUBLIC;
REVOKE ALL ON SCHEMA member_db  FROM PUBLIC;
REVOKE ALL ON SCHEMA catalog_db FROM PUBLIC;
REVOKE ALL ON SCHEMA order_db   FROM PUBLIC;

GRANT USAGE, CREATE ON SCHEMA member_db  TO member_svc;
GRANT USAGE, CREATE ON SCHEMA catalog_db TO catalog_svc;
GRANT USAGE, CREATE ON SCHEMA order_db   TO order_svc;

-- 각 계정은 자기 스키마가 기본 search_path 가 된다.
ALTER ROLE member_svc  SET search_path = member_db;
ALTER ROLE catalog_svc SET search_path = catalog_db;
ALTER ROLE order_svc   SET search_path = order_db;

-- 이후 생성되는 테이블/시퀀스에도 소유 계정 권한이 자동 적용되도록.
ALTER DEFAULT PRIVILEGES IN SCHEMA member_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO member_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalog_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO catalog_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO order_svc;
