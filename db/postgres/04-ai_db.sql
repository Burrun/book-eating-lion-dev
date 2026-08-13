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
--
-- ⚠️ 로컬은 이 파일로 만들어지지 않는다. docker-compose 가 01~04 를 initdb 에 마운트하지
-- 않고, 로컬 스키마는 ddl-auto: update 가 엔티티를 보고 만든다. 그래서 여기 선언한
-- 제약(특히 fed_books 의 FK)이 로컬 DB 에는 존재하지 않는다 — 엔티티에 @ManyToOne 이
-- 없기 때문이다. 이 파일을 근거로 "제약이 막아주겠지"라고 가정하면 안 된다.
-- =============================================================================
SET search_path = ai_db;

CREATE TABLE lions (
    lion_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Cognito sub(UUID 문자열)를 담는다. member_db 의 숫자 PK 가 아니다.
    -- 컬럼명을 member_id 로 두는 것은 다른 스키마와 어휘를 맞추기 위해서다.
    -- 타입이 VARCHAR 라 member_db.members.member_id(BIGINT)와 비교하면 Postgres 가 막는다.
    member_id    VARCHAR(255) NOT NULL,  -- FK 없음: member_db 경계 밖. 값만 유지하고 조인하지 않는다
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
    -- 원본 파일의 SHA-256. 같은 파일이 다시 오면 임베딩을 건너뛴다.
    -- 임베딩은 청크당 1회이고 장편은 200~500청크라, 이 컬럼이 없으면 재등록마다 전액 재과금이다.
    source_hash  VARCHAR(64),
    ingested_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 그 책이 인덱스에 넣은 벡터 키 목록.
--
-- 🔴 이게 없으면 재적재 시 삭제 대상을 찾으려고 ListVectors 로 인덱스를 전수 스캔해야 한다.
-- (ListVectors 에는 prefix 파라미터가 없다.) 배치는 런당 1회라 견뎠지만, 책마다 트리거되는
-- 자동 인제스트에서는 책 1권당 전수 스캔이 되어 인덱스 크기에 선형으로 느려진다.
CREATE TABLE wiki_book_chunks (
    book_id   BIGINT NOT NULL,
    page      INT NOT NULL,
    chunk_seq INT NOT NULL,
    PRIMARY KEY (book_id, page, chunk_seq),
    CONSTRAINT fk_wiki_book_chunks_book FOREIGN KEY (book_id)
        REFERENCES wiki_books (book_id) ON DELETE CASCADE
);

-- 누가 무엇을 샀는가. 🔴 질의 시 이게 그대로 접근 제어 필터가 된다.
--
-- order-service 가 결제 확정 후 SQS 로 알린 것을 적재한다. catalog·order 를 호출하지 않는다 —
-- "읽을 수 있다"는 곧 "우리가 구매 이벤트를 받았다"이고 그건 ai_db 가 안다.
--
-- 읽기는 Redis Set 으로 한다 — 질의마다 여기를 SELECT 하면 커넥션이 열려
-- Serverless v2 auto-pause 가 영원히 안 걸린다. 여기는 원본일 뿐이다.
CREATE TABLE purchased_books (
    member_id    VARCHAR(255) NOT NULL,  -- FK 없음: member_db 경계 밖. 값은 Cognito sub 다
    book_id      BIGINT NOT NULL,        -- FK 없음: catalog_db 경계 밖
    purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 같은 구매 이벤트가 두 번 배달돼도(SQS at-least-once) 행이 늘지 않는다.
    PRIMARY KEY (member_id, book_id)
);

CREATE INDEX idx_purchased_books_member ON purchased_books (member_id);

-- 누가 무엇을 먹였는가. 라이언 경험치용이다.
--
-- ⚠️ 이 테이블은 더 이상 검색 권한이 아니다. 권한은 purchased_books 로 옮겼다 —
-- 사자를 안 키워도 산 책은 읽을 수 있어야 하고, 먹였다고 안 산 책을 읽을 수는 없다.
CREATE TABLE fed_books (
    member_id VARCHAR(255) NOT NULL,   -- FK 없음: member_db 경계 밖. 값은 Cognito sub 다
    book_id   BIGINT NOT NULL,
    fed_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, book_id),  -- 먹이기 멱등성이 여기서 나온다
    -- 🔴 ON DELETE CASCADE 를 쓰지 않는다(기본값 RESTRICT).
    --
    -- 먹인 기록은 "읽을 권한"이다. 판매중지나 카탈로그 삭제는 판매의 결정이지 이미 획득한
    -- 권한을 뺏는 근거가 아니다. CASCADE 면 책 하나 내리는 순간 그 책을 먹은 사용자의
    -- 기록이 말없이 사라지는데, 그 책으로 받은 exp 는 라이언에 남아 있어 경험치와
    -- 먹은 책 수가 어긋난다.
    --
    -- 그래서 wiki_books 행은 원칙적으로 지우지 않는다. 지워야 할 상황이 오면 RESTRICT 가
    -- 막아서 "이 책을 먹은 사람이 있는데 어쩔 것인가"를 먼저 결정하게 한다.
    -- 목록에서만 내리고 싶다면 삭제가 아니라 상태 컬럼으로 풀 문제다(docs/TODOS.md 4번).
    CONSTRAINT fk_fed_books_wiki FOREIGN KEY (book_id)
        REFERENCES wiki_books (book_id)
);

CREATE INDEX idx_fed_books_member ON fed_books (member_id);

-- inquiries 테이블은 없앴다.
--
-- 1:1 문의를 받아 두기만 하고 답변할 수단(관리자 화면)이 끝내 없었다. 사용자는 답을
-- 기다리는데 아무도 그 행의 존재를 모르는 상태였고, InquiryBotService.reply() 도 호출자가
-- 없는 죽은 코드였다. 상담사가 없을 때는 문의 게시판으로 안내만 하고, 문의 자체는
-- 이 서비스가 보관하지 않는다.

CREATE TABLE faqs (
    faq_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category   VARCHAR(100) NOT NULL,
    question   VARCHAR(500) NOT NULL,
    answer     TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
