SET NAMES utf8mb4;

DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS inquiries;
DROP TABLE IF EXISTS restock_notifications;
DROP TABLE IF EXISTS book_swipes;
DROP TABLE IF EXISTS subscription_deliveries;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS lion_memories;
DROP TABLE IF EXISTS lions;
DROP TABLE IF EXISTS settlements;
DROP TABLE IF EXISTS used_book_payments;
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS chat_rooms;
DROP TABLE IF EXISTS used_books;
DROP TABLE IF EXISTS deliveries;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS wishlists;
DROP TABLE IF EXISTS recent_books;
DROP TABLE IF EXISTS point_histories;
DROP TABLE IF EXISTS member_coupons;
DROP TABLE IF EXISTS coupons;
DROP TABLE IF EXISTS premium_memberships;
DROP TABLE IF EXISTS cards;
DROP TABLE IF EXISTS webtoon_cuts;
DROP TABLE IF EXISTS book_webtoons;
DROP TABLE IF EXISTS books;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS addresses;
DROP TABLE IF EXISTS faqs;
DROP TABLE IF EXISTS members;

CREATE TABLE members (
    member_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password      VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(30)  NULL,
    gender        ENUM('MALE', 'FEMALE') DEFAULT 'MALE',
    age           INT NULL,
    role          ENUM('USER', 'ADMIN')     NOT NULL DEFAULT 'USER',
    grade         ENUM('BASIC', 'PREMIUM')  NOT NULL DEFAULT 'BASIC',
    point_balance BIGINT NOT NULL DEFAULT 0,
    is_deleted    TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMP NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_members_username UNIQUE (username),
    CONSTRAINT uk_members_nickname UNIQUE (nickname),
    CONSTRAINT uk_members_email    UNIQUE (email),
    CONSTRAINT chk_members_age CHECK (age IS NULL OR age BETWEEN 1 AND 150)
);

CREATE TABLE addresses (
    address_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id       BIGINT NOT NULL,
    recipient_name  VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30)  NOT NULL,
    postal_code     VARCHAR(20)  NOT NULL,
    address         VARCHAR(255) NOT NULL,
    address_detail  VARCHAR(255) NULL,
    is_default      TINYINT(1) NOT NULL DEFAULT 0,
    default_flag    TINYINT GENERATED ALWAYS AS (IF(is_default = 1, 1, NULL)) STORED,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_addresses_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT uk_addresses_default UNIQUE (member_id, default_flag)
);

CREATE TABLE categories (
    category_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    parent_id     BIGINT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id)
        REFERENCES categories (category_id) ON DELETE SET NULL
);

CREATE TABLE books (
    book_id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title                VARCHAR(200) NOT NULL,
    author               VARCHAR(100) NOT NULL,
    publisher            VARCHAR(100) NOT NULL,
    isbn                 CHAR(13) NOT NULL,
    price                BIGINT NOT NULL,
    stock                INT NOT NULL DEFAULT 0,
    category_id          BIGINT NOT NULL,
    description          TEXT NULL,
    detailed_synopsis    TEXT NULL,
    image_url            VARCHAR(500) NULL,
    sale_status          ENUM('ON_SALE', 'STOPPED', 'OUT_OF_STOCK') NOT NULL DEFAULT 'ON_SALE',
    published_date       DATE NULL,
    sales_count          INT NOT NULL DEFAULT 0,
    rating_avg           DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    review_count         INT NOT NULL DEFAULT 0,
    is_deleted           TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at           TIMESTAMP NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id) ON DELETE RESTRICT,
    CONSTRAINT chk_books_stock CHECK (stock >= 0),
    CONSTRAINT chk_books_price CHECK (price >= 0),
    CONSTRAINT chk_books_rating_avg CHECK (rating_avg BETWEEN 0 AND 5),
    CONSTRAINT chk_books_review_count CHECK (review_count >= 0)
);

CREATE TABLE book_webtoons (
    webtoon_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id           BIGINT NOT NULL,
    version           INT    NOT NULL DEFAULT 1,
    generation_status ENUM('PENDING', 'GENERATING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    ai_model          VARCHAR(100) NULL,
    source_prompt     TEXT         NULL,
    total_cuts        INT NOT NULL DEFAULT 0,
    access_level      ENUM('PUBLIC', 'PURCHASER', 'PREMIUM') NOT NULL DEFAULT 'PREMIUM',
    is_active         TINYINT(1) NOT NULL DEFAULT 0,
    active_flag       TINYINT GENERATED ALWAYS AS (IF(is_active = 1, 1, NULL)) STORED,
    failure_reason    VARCHAR(500) NULL,
    generated_at      TIMESTAMP NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_webtoons_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_book_webtoons_version UNIQUE (book_id, version),
    CONSTRAINT uk_book_webtoons_active  UNIQUE (book_id, active_flag),
    CONSTRAINT chk_book_webtoons_version CHECK (version > 0)
);

CREATE TABLE webtoon_cuts (
    webtoon_cut_id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    recent_book_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id      BIGINT NOT NULL,
    book_id        BIGINT NOT NULL,
    viewed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recent_books_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_recent_books_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_recent_books_member_book UNIQUE (member_id, book_id)
);

CREATE TABLE wishlists (
    wishlist_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT NOT NULL,
    book_id     BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wishlists_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlists_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_wishlists_member_book UNIQUE (member_id, book_id)
);

CREATE TABLE cards (
    card_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id          BIGINT NOT NULL,
    card_company       VARCHAR(100) NULL,
    card_token         VARCHAR(255) NOT NULL,
    masked_card_number VARCHAR(19)  NOT NULL,
    card_status        ENUM('ACTIVE', 'SUSPENDED', 'TERMINATED') NOT NULL DEFAULT 'ACTIVE',
    monthly_limit      BIGINT NOT NULL,
    current_usage      BIGINT NOT NULL DEFAULT 0,
    virtual_balance    BIGINT NOT NULL DEFAULT 0,
    is_default         TINYINT(1) NOT NULL DEFAULT 0,
    default_flag       TINYINT GENERATED ALWAYS AS (IF(is_default = 1, 1, NULL)) STORED,
    issued_date        DATE NOT NULL,
    expiry_date        DATE NOT NULL,
    is_deleted         TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at         TIMESTAMP NULL,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_cards_token UNIQUE (card_token),
    CONSTRAINT fk_cards_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT chk_cards_balance CHECK (virtual_balance >= 0),
    CONSTRAINT chk_cards_expiry CHECK (expiry_date > issued_date),
    CONSTRAINT uk_cards_default UNIQUE (member_id, default_flag)
);

CREATE TABLE premium_memberships (
    membership_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id      BIGINT NOT NULL,
    card_id        BIGINT NULL,
    plan_type      ENUM('MONTHLY', 'YEARLY') NOT NULL,
    payment_amount BIGINT NOT NULL,
    payment_method  ENUM('CARD', 'KAKAOPAY') NOT NULL DEFAULT 'CARD',
    pg_tid          VARCHAR(100) NULL,
    approval_number VARCHAR(50)  NULL,
    payment_status  ENUM('APPROVED', 'DECLINED', 'CANCELLED') NOT NULL DEFAULT 'APPROVED',
    idempotency_key VARCHAR(64)  NULL,
    start_at       TIMESTAMP NOT NULL,
    end_at         TIMESTAMP NOT NULL,
    auto_renew     TINYINT(1) NOT NULL DEFAULT 0,
    status         ENUM('ACTIVE', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_premium_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_premium_card FOREIGN KEY (card_id)
        REFERENCES cards (card_id) ON DELETE SET NULL,
    CONSTRAINT uk_premium_approval    UNIQUE (approval_number),
    CONSTRAINT uk_premium_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_premium_period CHECK (end_at > start_at)
);

CREATE TABLE coupons (
    coupon_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_code          VARCHAR(100) NOT NULL,
    coupon_name          VARCHAR(255) NOT NULL,
    discount_amount      BIGINT NOT NULL,
    minimum_order_amount BIGINT NOT NULL DEFAULT 0,
    expires_at           TIMESTAMP NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_coupons_code UNIQUE (coupon_code),
    CONSTRAINT chk_coupons_discount CHECK (discount_amount > 0)
);

CREATE TABLE member_coupons (
    member_coupon_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id        BIGINT NOT NULL,
    coupon_id        BIGINT NOT NULL,
    is_used          TINYINT(1) NOT NULL DEFAULT 0,
    used_at          TIMESTAMP NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_coupons_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_member_coupons_coupon FOREIGN KEY (coupon_id)
        REFERENCES coupons (coupon_id) ON DELETE RESTRICT,
    CONSTRAINT uk_member_coupons UNIQUE (member_id, coupon_id)
);

CREATE TABLE point_histories (
    point_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id        BIGINT NOT NULL,
    amount           BIGINT NOT NULL,
    ref_type         ENUM('ORDER', 'ORDER_CANCEL', 'REVIEW', 'MEMBERSHIP', 'EVENT', 'ADMIN') NULL,
    ref_id           BIGINT NULL,
    reason           VARCHAR(500) NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_point_histories_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT
);

CREATE TABLE cart_items (
    cart_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id    BIGINT NOT NULL,
    book_id      BIGINT NOT NULL,
    quantity     INT NOT NULL DEFAULT 1,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_items_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_cart_items_member_book UNIQUE (member_id, book_id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)
);

CREATE TABLE orders (
    order_id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id              BIGINT NOT NULL,
    address_id             BIGINT NULL,
    member_coupon_id       BIGINT NULL,
    recipient_name         VARCHAR(100) NOT NULL,
    recipient_phone        VARCHAR(30)  NOT NULL,
    postal_code            VARCHAR(20)  NOT NULL,
    address                VARCHAR(255) NOT NULL,
    address_detail         VARCHAR(255) NULL,
    items_subtotal         BIGINT NOT NULL DEFAULT 0,
    total_amount           BIGINT NOT NULL,
    coupon_discount_amount BIGINT NOT NULL DEFAULT 0,
    used_point             BIGINT NOT NULL DEFAULT 0,
    order_status           ENUM(
        'PENDING_PAYMENT',
        'PAID',
        'PAYMENT_FAILED',
        'PREPARING',
        'SHIPPED',
        'DELIVERED',
        'CANCEL_REQUESTED',
        'CANCELLED',
        'EXCHANGE_REQUESTED',
        'EXCHANGED',
        'RETURN_REQUESTED',
        'RETURNED',
        'REFUND_REQUESTED',
        'REFUNDED'
    ) NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_address FOREIGN KEY (address_id)
        REFERENCES addresses (address_id) ON DELETE SET NULL,
    CONSTRAINT fk_orders_member_coupon FOREIGN KEY (member_coupon_id)
        REFERENCES member_coupons (member_coupon_id) ON DELETE SET NULL,
    CONSTRAINT chk_orders_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_orders_discount CHECK (coupon_discount_amount >= 0 AND used_point >= 0),
    CONSTRAINT chk_orders_subtotal CHECK (items_subtotal >= 0),
    CONSTRAINT chk_orders_total
        CHECK (total_amount = items_subtotal - coupon_discount_amount - used_point)
);

CREATE TABLE order_items (
    order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT NOT NULL,
    book_id       BIGINT NOT NULL,
    quantity      INT NOT NULL,
    unit_price    BIGINT NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (unit_price >= 0)
);

CREATE TABLE reviews (
    review_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT NOT NULL,
    book_id       BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    rating        TINYINT NOT NULL,
    content       VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id)
        REFERENCES order_items (order_item_id) ON DELETE RESTRICT,
    CONSTRAINT uk_reviews_order_item UNIQUE (order_item_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE payments (
    payment_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    card_id         BIGINT NULL,
    payment_method  ENUM('CARD', 'KAKAOPAY') NOT NULL DEFAULT 'CARD',
    pg_tid          VARCHAR(100) NULL,
    approval_number VARCHAR(50) NULL,
    amount          BIGINT NOT NULL,
    payment_status  ENUM('APPROVED', 'DECLINED', 'CANCELLED') NOT NULL,
    decline_reason  VARCHAR(500) NULL,
    idempotency_key VARCHAR(64) NULL,
    approved_at     TIMESTAMP NULL,
    cancelled_at    TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_payments_approval_number UNIQUE (approval_number),
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_card FOREIGN KEY (card_id)
        REFERENCES cards (card_id) ON DELETE SET NULL,
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
);

CREATE TABLE deliveries (
    delivery_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    courier_company VARCHAR(100) NULL,
    tracking_number VARCHAR(100) NULL,
    delivery_status ENUM('READY', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED') NOT NULL DEFAULT 'READY',
    shipped_at      TIMESTAMP NULL,
    delivered_at    TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT uk_deliveries_order UNIQUE (order_id),
    CONSTRAINT uk_deliveries_tracking UNIQUE (courier_company, tracking_number)
);

CREATE TABLE used_books (
    used_book_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_id      BIGINT NOT NULL,
    book_id        BIGINT NOT NULL,
    sale_price     BIGINT NOT NULL,
    item_condition ENUM('S', 'A', 'B') NOT NULL,
    image_url      VARCHAR(500) NULL,
    status         ENUM('ON_SALE', 'RESERVED', 'SOLD') NOT NULL DEFAULT 'ON_SALE',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_used_books_seller FOREIGN KEY (seller_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_used_books_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT chk_used_books_price CHECK (sale_price >= 0)
);

CREATE TABLE chat_rooms (
    chat_room_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    used_book_id       BIGINT NOT NULL,
    buyer_id           BIGINT NOT NULL,
    seller_id          BIGINT NOT NULL,
    transaction_status ENUM('ACTIVE', 'COMPLETED') NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_rooms_used_book FOREIGN KEY (used_book_id)
        REFERENCES used_books (used_book_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_rooms_buyer FOREIGN KEY (buyer_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_rooms_seller FOREIGN KEY (seller_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT uk_chat_rooms_used_book_buyer UNIQUE (used_book_id, buyer_id),
    CONSTRAINT chk_chat_rooms_not_self CHECK (buyer_id <> seller_id)
);

CREATE TABLE chat_messages (
    message_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT NOT NULL,
    sender_id    BIGINT NOT NULL,
    message      TEXT NOT NULL,
    is_read      TINYINT(1) NOT NULL DEFAULT 0,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_messages_room FOREIGN KEY (chat_room_id)
        REFERENCES chat_rooms (chat_room_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id)
        REFERENCES members (member_id) ON DELETE CASCADE
);

CREATE TABLE used_book_payments (
    used_book_payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    used_book_id         BIGINT NOT NULL,
    chat_room_id         BIGINT NULL,
    buyer_id             BIGINT NOT NULL,
    seller_id            BIGINT NOT NULL,
    card_id              BIGINT NULL,
    amount               BIGINT NOT NULL,
    payment_method       ENUM('CARD', 'KAKAOPAY') NOT NULL DEFAULT 'CARD',
    pg_tid               VARCHAR(100) NULL,
    approval_number      VARCHAR(50) NULL,
    payment_status       ENUM('APPROVED', 'DECLINED', 'CANCELLED') NOT NULL,
    escrow_status        ENUM('HELD', 'RELEASED', 'REFUNDED') NOT NULL DEFAULT 'HELD',
    decline_reason       VARCHAR(500) NULL,
    idempotency_key      VARCHAR(64) NULL,
    approved_at          TIMESTAMP NULL,
    released_at          TIMESTAMP NULL,
    cancelled_at         TIMESTAMP NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_used_book_payments_approval UNIQUE (approval_number),
    CONSTRAINT uk_used_book_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_ubp_used_book FOREIGN KEY (used_book_id)
        REFERENCES used_books (used_book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ubp_chat_room FOREIGN KEY (chat_room_id)
        REFERENCES chat_rooms (chat_room_id) ON DELETE SET NULL,
    CONSTRAINT fk_ubp_buyer FOREIGN KEY (buyer_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ubp_seller FOREIGN KEY (seller_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ubp_card FOREIGN KEY (card_id)
        REFERENCES cards (card_id) ON DELETE SET NULL,
    CONSTRAINT chk_ubp_amount CHECK (amount >= 0),
    CONSTRAINT chk_ubp_not_self CHECK (buyer_id <> seller_id)
);

CREATE TABLE settlements (
    settlement_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    used_book_id         BIGINT NOT NULL,
    used_book_payment_id BIGINT NOT NULL,
    seller_id            BIGINT NOT NULL,
    sale_price           BIGINT NOT NULL,
    platform_fee         BIGINT NOT NULL DEFAULT 0,
    settlement_amount    BIGINT NOT NULL,
    settlement_status    ENUM('PENDING', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    settled_at           TIMESTAMP NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlements_used_book FOREIGN KEY (used_book_id)
        REFERENCES used_books (used_book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_settlements_payment FOREIGN KEY (used_book_payment_id)
        REFERENCES used_book_payments (used_book_payment_id) ON DELETE RESTRICT,
    CONSTRAINT fk_settlements_seller FOREIGN KEY (seller_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT uk_settlements_used_book UNIQUE (used_book_id),
    CONSTRAINT uk_settlements_payment UNIQUE (used_book_payment_id),
    CONSTRAINT chk_settlements_amount
        CHECK (settlement_amount = sale_price - platform_fee AND platform_fee >= 0)
);

CREATE TABLE lions (
    lion_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id    BIGINT NOT NULL,
    level        INT NOT NULL DEFAULT 1,
    exp          BIGINT NOT NULL DEFAULT 0,
    growth_stage VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lions_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT uk_lions_member UNIQUE (member_id),
    CONSTRAINT chk_lions_level CHECK (level >= 1 AND exp >= 0)
);

CREATE TABLE lion_memories (
    lion_memory_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    lion_id         BIGINT NOT NULL,
    book_id         BIGINT NOT NULL,
    memo            TEXT NULL,
    quote_text      TEXT NULL,
    finished_at     TIMESTAMP NULL,
    embedding       JSON NULL,
    embedding_model VARCHAR(100) NULL,
    embedding_dim   INT NULL,
    embedding_ref   VARCHAR(255) NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lion_memories_lion FOREIGN KEY (lion_id)
        REFERENCES lions (lion_id) ON DELETE CASCADE,
    CONSTRAINT fk_lion_memories_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT
);

CREATE TABLE subscriptions (
    subscription_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id           BIGINT NOT NULL,
    plan_name           VARCHAR(100) NOT NULL,
    monthly_price       BIGINT NOT NULL,
    subscription_status ENUM('ACTIVE', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    next_delivery_date  DATE NULL,
    cancelled_at        TIMESTAMP NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_subscriptions_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT chk_subscriptions_price CHECK (monthly_price >= 0)
);

CREATE TABLE subscription_deliveries (
    subscription_delivery_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id          BIGINT NOT NULL,
    book_id                  BIGINT NOT NULL,
    delivery_round           INT NOT NULL,
    courier_company          VARCHAR(100) NULL,
    tracking_number          VARCHAR(100) NULL,
    delivery_status          ENUM('READY', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED') NOT NULL DEFAULT 'READY',
    shipped_at               TIMESTAMP NULL,
    delivered_at             TIMESTAMP NULL,
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_deliveries_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscriptions (subscription_id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_deliveries_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT uk_sub_deliveries_round UNIQUE (subscription_id, delivery_round),
    CONSTRAINT uk_sub_deliveries_tracking UNIQUE (courier_company, tracking_number)
);

CREATE TABLE book_swipes (
    book_swipe_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT NOT NULL,
    book_id       BIGINT NOT NULL,
    swipe_action  ENUM('LIKE', 'SKIP') NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_swipes_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_book_swipes_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE
);

CREATE TABLE restock_notifications (
    restock_notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id               BIGINT NOT NULL,
    book_id                 BIGINT NOT NULL,
    is_notified             TINYINT(1) NOT NULL DEFAULT 0,
    requested_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notified_at             TIMESTAMP NULL,
    CONSTRAINT fk_restock_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_restock_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE CASCADE,
    CONSTRAINT uk_restock_member_book UNIQUE (member_id, book_id)
);

CREATE TABLE inquiries (
    inquiry_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id            BIGINT NOT NULL,
    book_id              BIGINT NULL,
    title                VARCHAR(255) NOT NULL,
    content              TEXT NOT NULL,
    attachment_image_url VARCHAR(500) NULL,
    admin_answer         TEXT NULL,
    answered_by          BIGINT NULL,
    is_answered          TINYINT(1) NOT NULL DEFAULT 0,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    answered_at          TIMESTAMP NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inquiries_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_inquiries_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE SET NULL,
    CONSTRAINT fk_inquiries_admin FOREIGN KEY (answered_by)
        REFERENCES members (member_id) ON DELETE SET NULL
);

CREATE TABLE faqs (
    faq_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    category   VARCHAR(100) NOT NULL,
    question   VARCHAR(500) NOT NULL,
    answer     TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    audit_log_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id      BIGINT NOT NULL,
    action        VARCHAR(255) NOT NULL,
    target_type   VARCHAR(50) NULL,
    target_id     BIGINT NULL,
    ip_address    VARCHAR(45) NULL,
    details       TEXT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_admin FOREIGN KEY (admin_id)
        REFERENCES members (member_id) ON DELETE RESTRICT
);

CREATE INDEX idx_orders_member_created ON orders (member_id, created_at DESC);
CREATE INDEX idx_chat_messages_room_created ON chat_messages (chat_room_id, created_at DESC);
CREATE INDEX idx_books_sales_count ON books (sales_count DESC);
CREATE INDEX idx_books_published_date ON books (published_date DESC);
CREATE INDEX idx_recent_books_member_viewed ON recent_books (member_id, viewed_at DESC);
CREATE INDEX idx_reviews_book_created ON reviews (book_id, created_at DESC);
CREATE INDEX idx_books_rating_avg ON books (rating_avg DESC, review_count DESC);
CREATE INDEX idx_book_swipes_member_created ON book_swipes (member_id, created_at DESC);
CREATE INDEX idx_used_books_status_created ON used_books (status, created_at DESC);
CREATE INDEX idx_member_coupons_member_used ON member_coupons (member_id, is_used);
CREATE INDEX idx_point_histories_member_created ON point_histories (member_id, created_at DESC);
CREATE INDEX idx_book_webtoons_book_active ON book_webtoons (book_id, is_active, generation_status);
CREATE INDEX idx_used_books_status_created ON used_books (status, created_at DESC);