-- =============================================================================
-- order_db — order-service 전용
--   inventory, orders, order_items, payments, deliveries, cart_items,
--   coupons, member_coupons, subscriptions, subscription_deliveries
--
-- 이 스키마의 핵심은 inventory 다 (Phase 0-1 / 판단 ③).
-- 재고를 order 가 소유하므로 Redlock·재고차감·결제가 전부 단일 서비스 로컬
-- 트랜잭션이 된다. Saga 와 보상 트랜잭션이 통째로 사라지는 자리다.
--
-- 참고: subscriptions / subscription_deliveries 는 계획서 §2 판단 ② 표에
-- 명시되지 않았다. 정기구독은 결제·배송이 붙는 거래 관심사이므로 order_db 로
-- 귀속시켰다 (Phase 0-2 완료 조건 "모든 테이블이 정확히 한 스키마에 귀속").
--
-- member_id 는 전부 VARCHAR(255) 이고 값은 Cognito sub 다. 숫자 member_id 가 아니다.
-- Cognito PreTokenGeneration 이 없어 JWT 에 member_id 커스텀 클레임이 들어오지
-- 않았고, sub 로 식별하도록 전환했다. 엔티티도 String 이다(@Column length = 255).
--
-- subscriptions 는 아직 대응 엔티티가 없다. 정기배송(구독박스)의 소유 서비스를
-- order 로 확정했으므로 자리는 여기 남긴다 — member_db.premium_memberships(유료 멤버십)와
-- 별개 개념이다(docs/frontend/mypage-unimplemented.md §4).
-- =============================================================================
SET search_path = order_db;

-- ---------------------------------------------------------------------------
-- inventory — books.stock 에서 이관됐다.
--
-- 쓰기 주체가 ①관리자 입고 ②주문 차감 둘뿐이고, 둘 다 이 서비스 안에 있다.
-- 안내서[R1]의 "동일 데이터의 CRUD 거래가 서로 다른 서비스로 분리되지 않도록"
-- 원칙을 그대로 만족한다.
-- ---------------------------------------------------------------------------
CREATE TABLE inventory (
    book_id     BIGINT PRIMARY KEY,   -- FK 없음: catalog_db.books 경계 밖
    stock       INT NOT NULL DEFAULT 0,
    -- 낙관적 락. 분산 락(ElastiCache Redlock)과 별개로 DB 레벨 2차 방어선.
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_stock CHECK (stock >= 0)
);

CREATE TABLE coupons (
    coupon_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coupon_code          VARCHAR(100) NOT NULL,
    coupon_name          VARCHAR(255) NOT NULL,
    discount_amount      BIGINT NOT NULL,
    minimum_order_amount BIGINT NOT NULL DEFAULT 0,
    expires_at           TIMESTAMP NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coupons_code UNIQUE (coupon_code),
    CONSTRAINT chk_coupons_discount CHECK (discount_amount > 0)
);

CREATE TABLE member_coupons (
    member_coupon_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id        VARCHAR(255) NOT NULL,  -- FK 없음: member_db 경계 밖. 값은 Cognito sub 다
    coupon_id        BIGINT NOT NULL,
    is_used          BOOLEAN NOT NULL DEFAULT FALSE,
    used_at          TIMESTAMP NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_coupons_coupon FOREIGN KEY (coupon_id)
        REFERENCES coupons (coupon_id) ON DELETE RESTRICT,
    CONSTRAINT uk_member_coupons UNIQUE (member_id, coupon_id)
);

CREATE TABLE cart_items (
    cart_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id    VARCHAR(255) NOT NULL,  -- FK 없음: member_db 경계 밖. 값은 Cognito sub 다
    book_id      BIGINT NOT NULL,  -- FK 없음: catalog_db 경계 밖
    quantity     INT NOT NULL DEFAULT 1,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_items_member_book UNIQUE (member_id, book_id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)
);

CREATE TABLE orders (
    order_id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id              VARCHAR(255) NOT NULL,  -- FK 없음: member_db 경계 밖. 값은 Cognito sub 다
    address_id             BIGINT NULL,      -- FK 없음. 아래 배송지 칼럼이 주문 시점 스냅샷이다
    member_coupon_id       BIGINT NULL,
    recipient_name         VARCHAR(100) NOT NULL,
    recipient_phone        VARCHAR(30)  NOT NULL,
    postal_code            VARCHAR(20)  NOT NULL,
    address                VARCHAR(255) NOT NULL,
    address_detail         VARCHAR(255) NULL,
    delivery_request       VARCHAR(255) NULL,
    items_subtotal         BIGINT NOT NULL DEFAULT 0,
    total_amount           BIGINT NOT NULL,
    coupon_discount_amount BIGINT NOT NULL DEFAULT 0,
    used_point             BIGINT NOT NULL DEFAULT 0,
    order_status           VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_member_coupon FOREIGN KEY (member_coupon_id)
        REFERENCES member_coupons (member_coupon_id) ON DELETE SET NULL,
    CONSTRAINT chk_orders_status CHECK (order_status IN (
        'PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'PREPARING', 'SHIPPED', 'DELIVERED',
        'CANCEL_REQUESTED', 'CANCELLED', 'EXCHANGE_REQUESTED', 'EXCHANGED',
        'RETURN_REQUESTED', 'RETURNED', 'REFUND_REQUESTED', 'REFUNDED')),
    CONSTRAINT chk_orders_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_orders_discount CHECK (coupon_discount_amount >= 0 AND used_point >= 0),
    CONSTRAINT chk_orders_subtotal CHECK (items_subtotal >= 0),
    CONSTRAINT chk_orders_total
        CHECK (total_amount = items_subtotal - coupon_discount_amount - used_point)
);

CREATE TABLE order_items (
    order_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT NOT NULL,
    book_id       BIGINT NOT NULL,   -- FK 없음: catalog_db 경계 밖
    -- 주문 시점 도서 스냅샷. 원본이 바뀌어도 갱신하지 않는다(안내서[R1]의 스냅샷 예외).
    book_title    VARCHAR(200) NULL,
    quantity      INT NOT NULL,
    unit_price    BIGINT NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (unit_price >= 0)
);

CREATE TABLE payments (
    payment_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    card_id         BIGINT NULL,      -- FK 없음: member_db.cards 경계 밖
    payment_method  VARCHAR(10) NOT NULL DEFAULT 'CARD',
    pg_tid          VARCHAR(100) NULL,
    approval_number VARCHAR(50) NULL,
    amount          BIGINT NOT NULL,
    payment_status  VARCHAR(10) NOT NULL,
    decline_reason  VARCHAR(500) NULL,
    idempotency_key VARCHAR(64) NULL,
    approved_at     TIMESTAMP NULL,
    cancelled_at    TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payments_approval_number UNIQUE (approval_number),
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT chk_payments_method CHECK (payment_method IN ('CARD', 'KAKAOPAY')),
    CONSTRAINT chk_payments_status CHECK (payment_status IN ('APPROVED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
);

-- delivery 모듈이 order 로 병합되면서(Phase 1) 이 테이블도 order_db 로 들어온다.
CREATE TABLE deliveries (
    delivery_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    courier_company VARCHAR(100) NULL,
    tracking_number VARCHAR(100) NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'READY',
    shipped_at      TIMESTAMP NULL,
    delivered_at    TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT uk_deliveries_order UNIQUE (order_id),
    CONSTRAINT uk_deliveries_tracking UNIQUE (courier_company, tracking_number),
    CONSTRAINT chk_deliveries_status CHECK (delivery_status IN
        ('READY', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED'))
);

-- restock_notifications 는 삭제했다.
-- 재입고 알림은 catalog_db.restock_alerts 가 이미 소유하고 있고(modules/book 의
-- RestockAlert / RestockAlertService / GET /api/catalog/restock-alerts/me),
-- 여기 있던 테이블은 대응 엔티티가 없어 아무도 읽고 쓰지 않았다.
-- 같은 관심사를 두 스키마가 나눠 갖는 상태를 남기면 어느 쪽이 진짜인지 알 수 없다.

CREATE TABLE subscriptions (
    subscription_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- FK 없음: member_db 경계 밖. 값은 Cognito sub 다
    member_id           VARCHAR(255) NOT NULL,
    plan_name           VARCHAR(100) NOT NULL,
    monthly_price       BIGINT NOT NULL,
    subscription_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    next_delivery_date  DATE NULL,
    cancelled_at        TIMESTAMP NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_subscriptions_status CHECK (subscription_status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_subscriptions_price CHECK (monthly_price >= 0)
);

CREATE TABLE subscription_deliveries (
    subscription_delivery_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id          BIGINT NOT NULL,
    book_id                  BIGINT NOT NULL,  -- FK 없음: catalog_db 경계 밖
    delivery_round           INT NOT NULL,
    courier_company          VARCHAR(100) NULL,
    tracking_number          VARCHAR(100) NULL,
    delivery_status          VARCHAR(20) NOT NULL DEFAULT 'READY',
    shipped_at               TIMESTAMP NULL,
    delivered_at             TIMESTAMP NULL,
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_deliveries_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscriptions (subscription_id) ON DELETE CASCADE,
    CONSTRAINT uk_sub_deliveries_round UNIQUE (subscription_id, delivery_round),
    CONSTRAINT uk_sub_deliveries_tracking UNIQUE (courier_company, tracking_number),
    CONSTRAINT chk_sub_deliveries_status CHECK (delivery_status IN
        ('READY', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED'))
);

CREATE INDEX idx_orders_member_created      ON orders (member_id, created_at DESC);
CREATE INDEX idx_member_coupons_member_used ON member_coupons (member_id, is_used);
