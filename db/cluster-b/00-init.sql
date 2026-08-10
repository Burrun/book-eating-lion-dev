-- =============================================================================
-- Aurora PostgreSQL 클러스터 B (Serverless v2, min 0 ACU) — AI 전용
--
-- 왜 이것만 클러스터를 가르는가 (판단 ②):
--   ① pgvector 가 필요하다 — lion_memories.embedding 은 벡터 유사도 검색이 본질이다.
--      원본(MySQL, JSON 칼럼)은 인덱스를 못 타서 전건 로드 후 앱에서 코사인 계산이
--      되고, CPU 와 Aurora I/O 를 동시에 먹었다.
--   ② 사용 패턴이 간헐적이다 — Serverless v2 auto-pause 로 과금을 독립시킬 수 있는
--      유일한 스키마다. 단, ai-service 의 HikariCP 에 minimum-idle: 0 을 함께
--      설정해야 실제로 멈춘다(Phase 2-8). 커넥션이 하나라도 물려 있으면
--      Aurora 는 영원히 idle 로 떨어지지 않는다.
--
-- 엔진은 클러스터 A 와 같은 PostgreSQL 이다. 엔진 통일과 클러스터 분리는 다른 레버다.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS ai_db;

CREATE ROLE ai_svc LOGIN PASSWORD 'ai_pw';

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA ai_db  FROM PUBLIC;

GRANT USAGE, CREATE ON SCHEMA ai_db TO ai_svc;
ALTER ROLE ai_svc SET search_path = ai_db;

ALTER DEFAULT PRIVILEGES IN SCHEMA ai_db
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ai_svc;
