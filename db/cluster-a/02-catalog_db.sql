-- =============================================================================
-- catalog_db — catalog-service 전용
--   categories, books, book_webtoons, webtoon_cuts, recent_books, wishlists,
--   reviews, book_swipes, review_permissions
--
-- 이 스키마에서 사라진 것 2가지:
--   ① books.stock       → order_db.inventory 로 이관 (Phase 0-1, 판단 ③)
--                          재고는 카탈로그 관심사가 아니라 거래 관심사다.
--   ② members 를 가리키던 FK 전부 → 값만 유지하고 제약은 제거 (경계 침범)
-- =============================================================================
SET search_path = catalog_db;

CREATE TABLE categories (
    category_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    parent_id     BIGINT NULL,
    sort_order    INT NOT NULL DEFAULT 0,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_categories_name UNIQUE (category_name),
    CONSTRAINT chk_categories_sort_order CHECK (sort_order >= 0),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id)
        REFERENCES categories (category_id) ON DELETE SET NULL
);

CREATE TABLE books (
    book_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    author            VARCHAR(100) NOT NULL,
    publisher         VARCHAR(100) NOT NULL,
    isbn              CHAR(13) NOT NULL,
    price             BIGINT NOT NULL,
    -- stock 없음. order_db.inventory 가 소유한다.
    category          VARCHAR(100) NOT NULL,
    description       TEXT NULL,
    detailed_synopsis TEXT NULL,
    cover_image_url   VARCHAR(500) NULL,
    sale_status       VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    published_date    DATE NULL,
    sales_count       INT NOT NULL DEFAULT 0,
    rating_avg        DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    review_count      INT NOT NULL DEFAULT 0,
    is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_books_isbn UNIQUE (isbn),
    CONSTRAINT chk_books_sale_status CHECK (sale_status IN ('ON_SALE', 'STOPPED', 'OUT_OF_STOCK')),
    CONSTRAINT chk_books_price CHECK (price >= 0),
    CONSTRAINT chk_books_rating_avg CHECK (rating_avg BETWEEN 0 AND 5),
    CONSTRAINT chk_books_review_count CHECK (review_count >= 0)
);

CREATE TABLE book_webtoons (
    webtoon_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id           BIGINT NOT NULL,
    version           INT    NOT NULL DEFAULT 1,
    generation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ai_model          VARCHAR(100) NULL,
    source_prompt     TEXT         NULL,
    total_cuts        INT NOT NULL DEFAULT 0,
    access_level      VARCHAR(20) NOT NULL DEFAULT 'PREMIUM',
    is_active         BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason    VARCHAR(500) NULL,
    generated_at      TIMESTAMP NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_webtoons_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_book_webtoons_version UNIQUE (book_id, version),
    CONSTRAINT chk_book_webtoons_gen CHECK (generation_status IN
        ('PENDING', 'GENERATING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_book_webtoons_access CHECK (access_level IN ('PUBLIC', 'PURCHASER', 'PREMIUM')),
    CONSTRAINT chk_book_webtoons_version CHECK (version > 0)
);

-- 도서당 활성 웹툰 1건 (MySQL 생성칼럼 트릭의 PostgreSQL 대응)
CREATE UNIQUE INDEX uk_book_webtoons_active ON book_webtoons (book_id) WHERE is_active;

CREATE TABLE webtoon_cuts (
    webtoon_cut_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    webtoon_id     BIGINT       NOT NULL,
    cut_order      INT          NOT NULL,
    image_url      VARCHAR(500) NOT NULL,
    dialogue       VARCHAR(500) NULL,
    scene_prompt   TEXT         NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webtoon_cuts_webtoon FOREIGN KEY (webtoon_id)
        REFERENCES book_webtoons (webtoon_id) ON DELETE CASCADE,
    CONSTRAINT uk_webtoon_cuts_order UNIQUE (webtoon_id, cut_order),
    CONSTRAINT chk_webtoon_cuts_order CHECK (cut_order > 0)
);

CREATE TABLE recent_books (
    recent_book_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id      VARCHAR(255) NOT NULL,  -- Cognito sub. FK 없음: member_db 경계 밖
    book_id        BIGINT NOT NULL,
    view_count     INT NOT NULL DEFAULT 1,
    viewed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recent_books_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_recent_books_member_book UNIQUE (member_id, book_id),
    CONSTRAINT chk_recent_books_view_count CHECK (view_count > 0)
);

CREATE TABLE wishlists (
    wishlist_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id   VARCHAR(255) NOT NULL,  -- Cognito sub. FK 없음: member_db 경계 밖
    book_id     BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wishlists_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_wishlists_member_book UNIQUE (member_id, book_id)
);

-- ---------------------------------------------------------------------------
-- review_permissions — 계획서 Phase 2 부록의 핵심 테이블
--
-- 리뷰 작성 시점에 "구매했나?"를 order-service 에 묻지 않는다. 구매 확정 시점에
-- order-service 가 ReviewPermissionGranted 이벤트로 권한을 미리 넘겨준다.
-- 덕분에 order-service 가 죽어도 리뷰 작성은 정상 동작한다(장애 격리 유지).
--
-- 여기 담기는 값은 전부 "그 시점의 스냅샷"이라 원본이 바뀌어도 갱신하지 않는다.
-- 동기화가 불필요한 게 아니라, 동기화가 틀린 동작인 케이스다.
-- ---------------------------------------------------------------------------
CREATE TABLE review_permissions (
    member_id     VARCHAR(255) NOT NULL,
    order_item_id BIGINT NOT NULL,   -- FK 아님. order_db 출처 추적용 값
    book_id       BIGINT NOT NULL,
    nickname      VARCHAR(50) NULL,  -- 작성자 표시용 스냅샷
    granted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at       TIMESTAMP NULL,    -- 1건당 1리뷰 강제
    PRIMARY KEY (member_id, order_item_id)
);

CREATE INDEX idx_review_permissions_member_book ON review_permissions (member_id, book_id);

CREATE TABLE reviews (
    review_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id     VARCHAR(255) NOT NULL,   -- Cognito sub. FK 없음: member_db 경계 밖
    book_id       BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,   -- FK 없음: order_db 경계 밖. review_permissions 로 검증
    nickname      VARCHAR(50) NULL,  -- 작성 당시 닉네임 스냅샷 (변경돼도 과거 리뷰는 그대로)
    rating        SMALLINT NOT NULL,
    content       VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_reviews_member_book UNIQUE (member_id, book_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE book_swipes (
    book_swipe_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id     VARCHAR(255) NOT NULL,  -- Cognito sub. FK 없음: member_db 경계 밖
    book_id       BIGINT NOT NULL,
    swipe_action  VARCHAR(10) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_swipes_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT chk_book_swipes_action CHECK (swipe_action IN ('LIKE', 'SKIP'))
);

CREATE INDEX idx_books_sales_count        ON books (sales_count DESC);
CREATE INDEX idx_books_published_date     ON books (published_date DESC);
CREATE INDEX idx_books_rating_avg         ON books (rating_avg DESC, review_count DESC);
CREATE INDEX idx_recent_books_member_viewed ON recent_books (member_id, viewed_at DESC);
CREATE INDEX idx_reviews_book_created     ON reviews (book_id, created_at DESC);
CREATE INDEX idx_book_swipes_member_created ON book_swipes (member_id, created_at DESC);
CREATE INDEX idx_book_webtoons_book_active ON book_webtoons (book_id, is_active, generation_status);
