-- =============================================================================
-- Aurora PostgreSQL — 클러스터 1개
--   catalog_db / order_db / member_db / ai_db  4개 스키마
--
-- 물리 분리는 하지 않고 "스키마 + 계정 권한"까지만 간다.
-- 각 서비스 계정은 자기 스키마에만 USAGE 권한이 있으므로, 남의 테이블에 조인을
-- 걸면 컴파일 타임이 아니라 런타임에 즉시 권한 에러로 드러난다.
--
-- ai_db 는 원래 별도 클러스터(Serverless v2)였다. 근거가 둘이었는데 둘 다 없어졌다 —
--   ① pgvector 가 필요하다     -> 벡터를 전부 S3 Vectors 로 옮겨서 불필요
--   ② auto-pause 로 과금 독립  -> ①이 사라지자 클러스터를 하나 더 띄울 값을 못 한다
-- 격리를 만드는 건 클러스터가 아니라 아래의 계정 권한이다.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 스키마
-- ---------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS member_db;
CREATE SCHEMA IF NOT EXISTS catalog_db;
CREATE SCHEMA IF NOT EXISTS order_db;
CREATE SCHEMA IF NOT EXISTS ai_db;

-- ---------------------------------------------------------------------------
-- 서비스 계정
-- 운영에서는 Secrets Manager 가 주입한다. 아래 비밀번호는 로컬 전용이다.
-- ---------------------------------------------------------------------------
CREATE ROLE member_svc  LOGIN PASSWORD 'member_pw';
CREATE ROLE catalog_svc LOGIN PASSWORD 'catalog_pw';
CREATE ROLE order_svc   LOGIN PASSWORD 'order_pw';
CREATE ROLE ai_svc      LOGIN PASSWORD 'ai_pw';

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
REVOKE ALL ON SCHEMA ai_db      FROM PUBLIC;

GRANT USAGE, CREATE ON SCHEMA member_db  TO member_svc;
GRANT USAGE, CREATE ON SCHEMA catalog_db TO catalog_svc;
GRANT USAGE, CREATE ON SCHEMA order_db   TO order_svc;
GRANT USAGE, CREATE ON SCHEMA ai_db      TO ai_svc;

-- 각 계정은 자기 스키마가 기본 search_path 가 된다.
ALTER ROLE member_svc  SET search_path = member_db;
ALTER ROLE catalog_svc SET search_path = catalog_db;
ALTER ROLE order_svc   SET search_path = order_db;
ALTER ROLE ai_svc      SET search_path = ai_db;

-- 이후 생성되는 테이블/시퀀스에도 소유 계정 권한이 자동 적용되도록.
ALTER DEFAULT PRIVILEGES IN SCHEMA member_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO member_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalog_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO catalog_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA order_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO order_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA ai_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ai_svc;
