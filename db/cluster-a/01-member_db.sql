-- =============================================================================
-- member_db — member-service 전용
--   members, addresses, cards, premium_memberships, point_histories, audit_logs
--
-- 포팅 규칙 (계획서 판단 ②):
--   AUTO_INCREMENT              → GENERATED ALWAYS AS IDENTITY
--   TINYINT(1)                  → BOOLEAN
--   ENUM(...)                   → VARCHAR + CHECK
--   ON UPDATE CURRENT_TIMESTAMP → 삭제. BaseEntity 의 @LastModifiedDate 가 이미 관리한다.
--   MySQL 생성칼럼 + UNIQUE 트릭 → 부분 유니크 인덱스(WHERE ...)
-- =============================================================================
SET search_path = member_db;

CREATE TABLE members (
    member_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cognito_sub   VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(30)  NULL,
    gender        VARCHAR(10) DEFAULT 'MALE',
    age           INT NULL,
    role          VARCHAR(10) NOT NULL DEFAULT 'USER',
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMP NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_members_cognito_sub UNIQUE (cognito_sub),
    CONSTRAINT uk_members_nickname UNIQUE (nickname),
    CONSTRAINT uk_members_email    UNIQUE (email),
    CONSTRAINT chk_members_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT chk_members_role   CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_members_age CHECK (age IS NULL OR age BETWEEN 1 AND 150)
);

CREATE TABLE addresses (
    address_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_sub      VARCHAR(255) NOT NULL,
    recipient_name  VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30)  NOT NULL,
    postal_code     VARCHAR(20)  NOT NULL,
    address         VARCHAR(255) NOT NULL,
    address_detail  VARCHAR(255) NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_addresses_member FOREIGN KEY (member_sub)
        REFERENCES members (cognito_sub) ON DELETE CASCADE
);

-- MySQL 원본은 생성칼럼(is_default=1 이면 1, 아니면 NULL)에 UNIQUE 를 걸어
-- "회원당 기본배송지 1건"을 강제했다. PostgreSQL 은 부분 인덱스로 직접 표현한다.
CREATE UNIQUE INDEX uk_addresses_default ON addresses (member_sub) WHERE is_default;

-- member_id(BIGINT, members.member_id FK) 대신 member_sub(members.cognito_sub FK)로 소유권을 식별한다.
-- addresses 테이블과 동일한 패턴 — Cognito PreTokenGeneration 의 member_id 커스텀 클레임 의존을 없애는 방향.
CREATE TABLE cards (
    card_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_sub         VARCHAR(255) NOT NULL,
    card_company       VARCHAR(100) NULL,
    card_token         VARCHAR(255) NOT NULL,
    masked_card_number VARCHAR(19)  NOT NULL,
    card_status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    monthly_limit      BIGINT NOT NULL,
    current_usage      BIGINT NOT NULL DEFAULT 0,
    virtual_balance    BIGINT NOT NULL DEFAULT 0,
    is_default         BOOLEAN NOT NULL DEFAULT FALSE,
    issued_date        DATE NOT NULL,
    expiry_date        DATE NOT NULL,
    is_deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at         TIMESTAMP NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cards_token UNIQUE (card_token),
    CONSTRAINT fk_cards_member FOREIGN KEY (member_sub)
        REFERENCES members (cognito_sub) ON DELETE CASCADE,
    CONSTRAINT chk_cards_status  CHECK (card_status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT chk_cards_balance CHECK (virtual_balance >= 0),
    CONSTRAINT chk_cards_expiry  CHECK (expiry_date > issued_date)
);

CREATE UNIQUE INDEX uk_cards_default ON cards (member_sub) WHERE is_default;

CREATE TABLE premium_memberships (
    membership_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id       BIGINT NOT NULL,
    card_id         BIGINT NULL,
    plan_type       VARCHAR(10) NOT NULL,
    payment_amount  BIGINT NOT NULL,
    payment_method  VARCHAR(10) NOT NULL DEFAULT 'CARD',
    pg_tid          VARCHAR(100) NULL,
    approval_number VARCHAR(50)  NULL,
    payment_status  VARCHAR(10) NOT NULL DEFAULT 'APPROVED',
    idempotency_key VARCHAR(64)  NULL,
    start_at        TIMESTAMP NOT NULL,
    end_at          TIMESTAMP NOT NULL,
    auto_renew      BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_premium_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_premium_card FOREIGN KEY (card_id)
        REFERENCES cards (card_id) ON DELETE SET NULL,
    CONSTRAINT uk_premium_approval    UNIQUE (approval_number),
    CONSTRAINT uk_premium_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_premium_plan   CHECK (plan_type IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT chk_premium_method CHECK (payment_method IN ('CARD', 'KAKAOPAY')),
    CONSTRAINT chk_premium_pay    CHECK (payment_status IN ('APPROVED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT chk_premium_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_premium_period CHECK (end_at > start_at)
);

CREATE TABLE point_histories (
    point_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id        BIGINT NOT NULL,
    amount           BIGINT NOT NULL,
    ref_type         VARCHAR(20) NULL,
    -- 경계 밖(order_db.orders 등)을 가리킬 수 있으므로 FK 를 걸지 않는다. 값만 유지.
    ref_id           BIGINT NULL,
    reason           VARCHAR(500) NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_point_histories_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT chk_point_histories_ref_type CHECK (ref_type IS NULL OR ref_type IN
        ('ORDER', 'ORDER_CANCEL', 'REVIEW', 'MEMBERSHIP', 'EVENT', 'ADMIN'))
);

CREATE TABLE audit_logs (
    audit_log_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id     BIGINT NOT NULL,
    action       VARCHAR(255) NOT NULL,
    target_type  VARCHAR(50) NULL,
    target_id    BIGINT NULL,
    ip_address   VARCHAR(45) NULL,
    details      TEXT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_admin FOREIGN KEY (admin_id)
        REFERENCES members (member_id) ON DELETE RESTRICT
);

CREATE INDEX idx_point_histories_member_created ON point_histories (member_id, created_at DESC);
