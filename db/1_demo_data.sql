-- 초기 데모 데이터 스크립트
-- 컬럼명은 JPA 엔티티(Member)의 필드명을 기준으로 한 Hibernate 기본 네이밍 전략(camelCase -> snake_case)을 따른다.

CREATE TABLE IF NOT EXISTS members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    gender ENUM('MALE', 'FEMALE') NOT NULL,
    age INT DEFAULT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    grade ENUM('BASIC', 'PREMIUM') NOT NULL DEFAULT 'BASIC',
    point BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 데모 계정 비밀번호는 'password1234'를 BCrypt로 암호화한 값이다.
INSERT INTO members (username, password, name, gender, age, role, grade, point)
VALUES ('testlion', '$2a$10$wL9vkmRwpIDuxJn8UuCE5egETGkZ8vrEfQeoQWo7l2RE3T8Q8Ph8m', '테스트유저', 'FEMALE', 25, 'USER', 'BASIC', 0);

INSERT INTO books (title, price) VALUES ('클라우드 엔지니어링 교재', 25000);
