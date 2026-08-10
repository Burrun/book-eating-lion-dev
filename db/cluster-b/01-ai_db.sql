-- =============================================================================
-- ai_db — ai-service 전용 (클러스터 B)
--   lions, lion_memories, inquiries, faqs
--
-- Phase 0-2b 확정 사항:
--   임베딩 모델 = Amazon Bedrock Titan Text Embeddings V2
--   차원        = 1024
--   → 외부 API 호출이므로 임베딩 생성은 I/O 바운드다.
--     따라서 ai-rag 의 HPA 는 CPU 가 아니라 "동시 요청 수" 기준으로 간다(판단 ④).
--     이 결정은 나중에 바꾸면 전건 재임베딩이 필요하므로 재고 소유권과 같은 등급으로 취급한다.
--
-- 이 스키마는 다른 스키마와 조인하지 않는다. 경계 FK 는 전부 스냅샷으로 끊었다.
-- =============================================================================
SET search_path = ai_db;

CREATE TABLE lions (
    lion_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id    BIGINT NOT NULL,  -- FK 없음: member_db 경계 밖. 값만 유지하고 조인하지 않는다
    level        INT NOT NULL DEFAULT 1,
    exp          BIGINT NOT NULL DEFAULT 0,
    growth_stage VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lions_member UNIQUE (member_id),
    CONSTRAINT chk_lions_level CHECK (level >= 1 AND exp >= 0)
);

CREATE TABLE lion_memories (
    lion_memory_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lion_id         BIGINT NOT NULL,
    book_id         BIGINT NOT NULL,   -- FK 없음: catalog_db 경계 밖
    -- 기록 생성 시점 스냅샷. 도서 제목이 바뀌어도 갱신하지 않는다.
    -- 저빈도 쓰기 경로라 값이 없으면 catalog 에 조회 1회면 충분하다(이벤트 불필요).
    book_title      VARCHAR(255) NULL,
    cover_url       VARCHAR(500) NULL,
    memo            TEXT NULL,
    quote_text      TEXT NULL,
    finished_at     TIMESTAMP NULL,
    -- 원본은 JSON 칼럼이라 인덱스를 못 탔다. pgvector 로 다시 짓는다.
    embedding       vector(1024) NULL,
    embedding_model VARCHAR(100) NULL DEFAULT 'amazon.titan-embed-text-v2:0',
    embedding_dim   INT NULL DEFAULT 1024,
    embedding_ref   VARCHAR(255) NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lion_memories_lion FOREIGN KEY (lion_id)
        REFERENCES lions (lion_id) ON DELETE CASCADE
);

-- HNSW + 코사인 거리. Phase 2-7 의 검증 기준은 EXPLAIN 에서 이 인덱스를 타는 것이다.
CREATE INDEX idx_lion_memories_embedding ON lion_memories
    USING hnsw (embedding vector_cosine_ops);

-- "프리미엄 회원의 최근 30일 메모 중 유사한 것" 같은 필터+벡터 하이브리드 질의를
-- SQL 한 줄로 처리하기 위한 보조 인덱스. 앱에서 후처리하지 않는다.
CREATE INDEX idx_lion_memories_lion_created ON lion_memories (lion_id, created_at DESC);

CREATE TABLE inquiries (
    inquiry_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id            BIGINT NOT NULL,  -- FK 없음: member_db 경계 밖
    nickname             VARCHAR(50) NULL, -- 작성 시점 스냅샷
    book_id              BIGINT NULL,      -- FK 없음: catalog_db 경계 밖
    book_title           VARCHAR(255) NULL,-- 생성 시점 스냅샷
    title                VARCHAR(255) NOT NULL,
    content              TEXT NOT NULL,
    attachment_image_url VARCHAR(500) NULL,
    admin_answer         TEXT NULL,
    answered_by          BIGINT NULL,      -- FK 없음: member_db 경계 밖
    is_answered          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    answered_at          TIMESTAMP NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inquiries_member_created ON inquiries (member_id, created_at DESC);

CREATE TABLE faqs (
    faq_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category   VARCHAR(100) NOT NULL,
    question   VARCHAR(500) NOT NULL,
    answer     TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
