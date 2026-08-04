-- 초기 데모 데이터 스크립트

CREATE TABLE IF NOT EXISTS members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cognito_sub VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    gender VARCHAR(10),
    birth_date DATE,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    grade VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    point INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS books (
    book_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    publisher VARCHAR(255) NOT NULL,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    category VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    cover_image_url VARCHAR(500),
    description TEXT,
    detailed_synopsis TEXT,
    sale_status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    published_date DATE NOT NULL,
    sales_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO members (cognito_sub, email, name, role, grade)
VALUES ('00000000-0000-0000-0000-000000000001', 'test@lion.com', '테스트유저', 'USER', 'BRONZE');

INSERT INTO books (
    title, author, publisher, isbn, category, price, stock_quantity,
    cover_image_url, description, detailed_synopsis, sale_status, published_date, sales_count
) VALUES (
    '클라우드 엔지니어링 교재',
    '북이팅라이언',
    '라이언출판사',
    '9791100000001',
    'IT/컴퓨터',
    25000,
    100,
    'https://example.com/covers/cloud-engineering.jpg',
    '클라우드 엔지니어링의 기초부터 실전까지 다루는 교재입니다.',
    '1장 클라우드 개론, 2장 컨테이너와 오케스트레이션, 3장 CI/CD 파이프라인 구축, 4장 관측성과 운영을 다루며, 마지막 장에서는 실제 장애 대응 사례를 상세히 재구성하여 소개한다.',
    'ON_SALE',
    '2026-01-15',
    42
);

CREATE TABLE IF NOT EXISTS reviews (
    review_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wishlists (
    wishlist_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wishlists_member_book (member_id, book_id)
);

CREATE TABLE IF NOT EXISTS recent_books (
    recent_book_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    viewed_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recent_books_member_book (member_id, book_id)
);
