-- =============================================================================
-- ai_db — ai-service 전용 스키마
--   lions, wiki_books, fed_books, inquiries, faqs
--
-- 벡터는 여기 없다. 전부 S3 Vectors 인덱스 wiki-v1 에 있다.
-- 이 스키마가 갖는 건 "누가 무엇을 먹였는가"(fed_books)와
-- "무엇이 인제스트됐는가"(wiki_books) 뿐이다 — 벡터는 공용, 권한은 관계형.
--
-- 임베딩 모델 = Bedrock Titan Text Embeddings V2 / 1024차원.
-- 외부 API 라 I/O 바운드이고, 그래서 HPA 는 CPU 가 아니라 동시 요청 수 기준이다.
-- 바꾸면 전건 재임베딩이 필요하므로 가볍게 건드리지 않는다.
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

-- 인제스트 완료 목록. "먹일 수 있는 책"의 정의가 곧 이 테이블이다.
-- catalog-service 를 호출하지 않고도 feedable-books 에 답할 수 있는 근거다.
CREATE TABLE wiki_books (
    book_id      BIGINT PRIMARY KEY,   -- FK 없음: catalog_db 경계 밖
    title        VARCHAR(255) NOT NULL,-- 인제스트 시점 스냅샷
    pages        INT NOT NULL,
    chunk_count  INT NOT NULL,         -- 인제스트 Job 의 건수 검증 기준값
    ingested_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 누가 무엇을 먹였는가. 질의 시 이게 그대로 접근 제어 필터가 된다.
-- 읽기는 Redis Set 으로 한다 — 질의마다 여기를 SELECT 하면 커넥션이 열려
-- Serverless v2 auto-pause 가 영원히 안 걸린다. 여기는 원본일 뿐이다.
CREATE TABLE fed_books (
    member_id  BIGINT NOT NULL,        -- FK 없음: member_db 경계 밖
    book_id    BIGINT NOT NULL,
    fed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, book_id),  -- 먹이기 멱등성이 여기서 나온다
    CONSTRAINT fk_fed_books_wiki FOREIGN KEY (book_id)
        REFERENCES wiki_books (book_id) ON DELETE CASCADE
);

CREATE INDEX idx_fed_books_member ON fed_books (member_id);

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
