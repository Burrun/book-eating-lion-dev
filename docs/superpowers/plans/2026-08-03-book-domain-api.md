# Book 도메인 조회 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `modules/book`의 `Book` 엔티티를 실제 서점 스키마 수준으로 확장하고, 목록/검색/상세/베스트셀러/신간/상세줄거리 6개 조회 API를 domain/repository/service/dto/controller 계층으로 구현한다.

**Architecture:** Spring Data JPA 리포지토리(파생 쿼리 + `@Query`) → 서비스 계층(엔티티→DTO 매핑, 트랜잭션) → `@RestController`(Spring Data `Pageable` 페이징) → `common.dto.ApiResponse<T>`로 감싼 JSON 응답. 존재하지 않는 책 조회는 `BookNotFoundException` → book 모듈 범위 `@RestControllerAdvice`가 404로 변환.

**Tech Stack:** Spring Boot 3.4.2, Java 21, Spring Data JPA, Lombok, JUnit 5 + Mockito + MockMvc, H2(테스트 전용), Gradle 8.12(wrapper: `backend/gradlew`)

**Reference:** 설계 문서 `docs/superpowers/specs/2026-08-03-book-domain-api-design.md` (사용자 승인 완료)

## Global Constraints

- 모든 API 응답은 `com.bookeatinglion.common.dto.ApiResponse<T>`(정적 메서드 `success(data)` / `error(message)`)로 감싼다. `common.response.ApiResponse`는 사용하지 않는다.
- 패키지 구조는 `com.bookeatinglion.book.{domain,repository,service,controller}` + 이번 작업에서 필요한 `dto`, `exception`을 추가한다.
- `category` 필드는 String(자유 입력)이다. Enum으로 만들지 않는다.
- 베스트셀러/신간은 `Book.salesCount`/`Book.publishedDate`(비정규화 필드) 기준으로 정렬한다. `order` 모듈을 참조하지 않는다(순환 의존 불가).
- `/synopsis/detail` 엔드포인트는 구매 인증 체크를 하지 않는다. 서비스 메서드 상단에 지정된 TODO 주석을 정확히 남긴다.
- 모든 명령은 `backend` 디렉터리에서 `./gradlew`(Windows: `gradlew.bat`, 이 리포는 Git Bash 기준이므로 `./gradlew` 사용)로 실행한다. 모듈 단위 태스크(`:modules:book:test` 등)를 사용하고, 루트 `build`는 사용하지 않는다 — `member` 모듈에 이번 작업과 무관한 기존 컴파일 에러(`MemberService.findByUsername`)가 있어 루트 전체 빌드는 현재 실패한다.
- `@WebMvcTest`는 `@AutoConfigureMockMvc(addFilters = false)`로 시큐리티 필터를 비활성화한다. 프로젝트 전체에 `SecurityFilterChain` 설정이 아직 없어 `spring-boot-starter-security` 기본값이 모든 요청에 인증을 요구하기 때문이며, 이 API들은 공개 조회용이므로 이번 범위에서 실제 보안 설정은 다루지 않는다.

---

## File Structure

```
backend/modules/book/
  build.gradle                                              (수정: H2 테스트 의존성 추가)
  src/main/java/com/bookeatinglion/book/
    domain/Book.java                                        (수정: 필드 확장)
    repository/BookRepository.java                          (수정: 쿼리 메서드 추가)
    dto/BookSummaryResponse.java                             (신규)
    dto/BookDetailResponse.java                              (신규)
    dto/BookSynopsisDetailResponse.java                      (신규)
    exception/BookNotFoundException.java                     (신규)
    service/BookService.java                                 (신규)
    controller/BookController.java                           (신규)
    controller/BookExceptionHandler.java                     (신규)
  src/test/java/com/bookeatinglion/book/
    BookModuleTestApplication.java                           (신규: 슬라이스 테스트용 최소 @SpringBootApplication)
    domain/BookPersistenceTest.java                          (신규)
    repository/BookRepositoryTest.java                       (신규)
    dto/BookDtoTest.java                                     (신규)
    service/BookServiceTest.java                             (신규)
    controller/BookControllerTest.java                       (신규)

db/1_demo_data.sql                                           (수정: books 스키마/INSERT 갱신)
```

---

### Task 1: Book 엔티티 확장 + 테스트 인프라 + 데모 SQL

**Files:**
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Book.java`
- Modify: `backend/modules/book/build.gradle`
- Modify: `db/1_demo_data.sql`
- Create: `backend/modules/book/src/test/java/com/bookeatinglion/book/BookModuleTestApplication.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/domain/BookPersistenceTest.java`

**Interfaces:**
- Consumes: 기존 `com.bookeatinglion.common.domain.BaseEntity`(createdAt/updatedAt), 기존 `com.bookeatinglion.book.domain.SaleStatus` enum.
- Produces: `Book` 엔티티 — getter: `getId()`, `getTitle()`, `getAuthor()`, `getPublisher()`, `getIsbn()`, `getCategory()`, `getPrice()`, `getStockQuantity()`, `getCoverImageUrl()`, `getDescription()`, `getDetailedSynopsis()`, `getSaleStatus()`, `getPublishedDate()`, `getSalesCount()`. 전체 필드를 받는 `@Builder` 생성자(`Book.builder()...build()`) — 이후 모든 Task의 테스트 픽스처 생성에 사용됨. `BookModuleTestApplication`(패키지 `com.bookeatinglion.book`) — 이후 모든 `@DataJpaTest`/`@WebMvcTest`가 이 클래스를 `@SpringBootConfiguration`으로 사용.

- [ ] **Step 1: book 모듈에 H2 테스트 의존성 추가**

`backend/modules/book/build.gradle`:

```groovy
dependencies {
    implementation project(':modules:common')

    testImplementation 'com.h2database:h2'
}
```

- [ ] **Step 2: 슬라이스 테스트용 최소 Spring Boot 설정 클래스 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/BookModuleTestApplication.java`:

```java
package com.bookeatinglion.book;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.bookeatinglion.book")
@EntityScan(basePackages = {"com.bookeatinglion.book", "com.bookeatinglion.common"})
@EnableJpaRepositories(basePackages = "com.bookeatinglion.book")
@EnableJpaAuditing
public class BookModuleTestApplication {
}
```

- [ ] **Step 3: 실패하는 영속성 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/domain/BookPersistenceTest.java`:

```java
package com.bookeatinglion.book.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = com.bookeatinglion.book.BookModuleTestApplication.class)
class BookPersistenceTest {

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void 모든_필드가_저장되고_조회된다() {
        Book book = Book.builder()
                .title("클라우드 엔지니어링 교재")
                .author("홍길동")
                .publisher("라이언출판사")
                .isbn("9791100000001")
                .category("IT/컴퓨터")
                .price(25000)
                .stockQuantity(100)
                .coverImageUrl("https://example.com/cover.jpg")
                .description("짧은 소개")
                .detailedSynopsis("상세 줄거리 본문")
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.of(2026, 1, 15))
                .salesCount(42)
                .build();

        entityManager.persist(book);
        entityManager.flush();
        entityManager.clear();

        Book found = entityManager.find(Book.class, book.getId());

        assertThat(found.getTitle()).isEqualTo("클라우드 엔지니어링 교재");
        assertThat(found.getAuthor()).isEqualTo("홍길동");
        assertThat(found.getPublisher()).isEqualTo("라이언출판사");
        assertThat(found.getIsbn()).isEqualTo("9791100000001");
        assertThat(found.getCategory()).isEqualTo("IT/컴퓨터");
        assertThat(found.getPrice()).isEqualTo(25000);
        assertThat(found.getStockQuantity()).isEqualTo(100);
        assertThat(found.getCoverImageUrl()).isEqualTo("https://example.com/cover.jpg");
        assertThat(found.getDescription()).isEqualTo("짧은 소개");
        assertThat(found.getDetailedSynopsis()).isEqualTo("상세 줄거리 본문");
        assertThat(found.getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
        assertThat(found.getPublishedDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(found.getSalesCount()).isEqualTo(42);
    }

    @Test
    void isbn은_유니크_제약이_걸려있다() {
        Book book1 = Book.builder()
                .title("책1").author("저자").publisher("출판사").isbn("9791100000099")
                .category("소설").price(10000).stockQuantity(1)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        Book book2 = Book.builder()
                .title("책2").author("저자").publisher("출판사").isbn("9791100000099")
                .category("소설").price(10000).stockQuantity(1)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();

        entityManager.persist(book1);
        entityManager.flush();

        entityManager.persist(book2);
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                entityManager::flush
        );
    }
}
```

- [ ] **Step 4: 테스트 실행하여 실패 확인**

Run (backend 디렉터리에서): `./gradlew :modules:book:test --tests "com.bookeatinglion.book.domain.BookPersistenceTest"`
Expected: FAIL — `Book.builder()` 및 `getAuthor()` 등 아직 존재하지 않는 심볼로 컴파일 실패.

- [ ] **Step 5: Book 엔티티 필드 확장**

`backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Book.java` 전체를 다음으로 교체:

```java
package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String publisher;

    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String detailedSynopsis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus saleStatus;

    @Column(nullable = false)
    private LocalDate publishedDate;

    @Column(nullable = false)
    private int salesCount;

    @Builder
    public Book(String title, String author, String publisher, String isbn, String category,
                int price, int stockQuantity, String coverImageUrl, String description,
                String detailedSynopsis, SaleStatus saleStatus, LocalDate publishedDate, int salesCount) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.isbn = isbn;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.detailedSynopsis = detailedSynopsis;
        this.saleStatus = saleStatus != null ? saleStatus : SaleStatus.ON_SALE;
        this.publishedDate = publishedDate;
        this.salesCount = salesCount;
    }
}
```

- [ ] **Step 6: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.domain.BookPersistenceTest"`
Expected: PASS (2 tests)

- [ ] **Step 7: 데모 SQL 갱신**

`db/1_demo_data.sql` 전체를 다음으로 교체:

```sql
-- 초기 데모 데이터 스크립트

CREATE TABLE IF NOT EXISTS members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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

INSERT INTO members (email, name) VALUES ('test@lion.com', '테스트유저');

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
```

- [ ] **Step 8: Commit**

```bash
git add backend/modules/book/build.gradle \
        backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Book.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/BookModuleTestApplication.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/domain/BookPersistenceTest.java \
        db/1_demo_data.sql
git commit -m "feat(book): Book 엔티티 필드 확장 및 데모 데이터 갱신"
```

---

### Task 2: BookRepository 쿼리 메서드

**Files:**
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/repository/BookRepository.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/repository/BookRepositoryTest.java`

**Interfaces:**
- Consumes: `Book`, `Book.builder()`, `SaleStatus` (Task 1), `BookModuleTestApplication` (Task 1).
- Produces: `BookRepository`에 `Page<Book> findByCategory(String category, Pageable pageable)`, `Page<Book> search(String q, Pageable pageable)`, `List<Book> findBySaleStatusOrderBySalesCountDesc(SaleStatus saleStatus, Pageable pageable)`, `List<Book> findBySaleStatusOrderByPublishedDateDesc(SaleStatus saleStatus, Pageable pageable)`.

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/repository/BookRepositoryTest.java`:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.save(book("스프링 입문", "김스프링", "IT/컴퓨터",
                LocalDate.of(2026, 1, 1), 10, SaleStatus.ON_SALE, "9791100000011"));
        bookRepository.save(book("자바의 정석", "남궁성", "IT/컴퓨터",
                LocalDate.of(2026, 3, 1), 100, SaleStatus.ON_SALE, "9791100000012"));
        bookRepository.save(book("어린 왕자", "생텍쥐페리", "소설",
                LocalDate.of(2026, 2, 1), 50, SaleStatus.ON_SALE, "9791100000013"));
        bookRepository.save(book("절판된 책", "익명", "소설",
                LocalDate.of(2020, 1, 1), 5, SaleStatus.STOPPED, "9791100000014"));
    }

    private Book book(String title, String author, String category, LocalDate publishedDate,
                       int salesCount, SaleStatus saleStatus, String isbn) {
        return Book.builder()
                .title(title).author(author).publisher("출판사").isbn(isbn)
                .category(category).price(10000).stockQuantity(10)
                .saleStatus(saleStatus).publishedDate(publishedDate).salesCount(salesCount)
                .build();
    }

    @Test
    void 카테고리로_필터링한다() {
        Page<Book> result = bookRepository.findByCategory("IT/컴퓨터", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("스프링 입문", "자바의 정석");
    }

    @Test
    void 제목이나_저자로_검색한다() {
        Page<Book> byTitle = bookRepository.search("스프링", PageRequest.of(0, 10));
        assertThat(byTitle.getContent()).extracting(Book::getTitle).containsExactly("스프링 입문");

        Page<Book> byAuthor = bookRepository.search("생텍쥐페리", PageRequest.of(0, 10));
        assertThat(byAuthor.getContent()).extracting(Book::getTitle).containsExactly("어린 왕자");
    }

    @Test
    void 판매량_내림차순으로_정렬한다() {
        List<Book> result = bookRepository.findBySaleStatusOrderBySalesCountDesc(
                SaleStatus.ON_SALE, PageRequest.of(0, 2));

        assertThat(result).extracting(Book::getTitle)
                .containsExactly("자바의 정석", "어린 왕자");
    }

    @Test
    void 출간일_내림차순으로_정렬한다() {
        List<Book> result = bookRepository.findBySaleStatusOrderByPublishedDateDesc(
                SaleStatus.ON_SALE, PageRequest.of(0, 2));

        assertThat(result).extracting(Book::getTitle)
                .containsExactly("자바의 정석", "어린 왕자");
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.BookRepositoryTest"`
Expected: FAIL — `findByCategory`/`search`/`findBySaleStatusOrderBySalesCountDesc`/`findBySaleStatusOrderByPublishedDateDesc` 심볼 없음으로 컴파일 실패.

- [ ] **Step 3: BookRepository에 쿼리 메서드 추가**

`backend/modules/book/src/main/java/com/bookeatinglion/book/repository/BookRepository.java` 전체를 다음으로 교체:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findByCategory(String category, Pageable pageable);

    @Query("select b from Book b where lower(b.title) like lower(concat('%', :q, '%')) " +
           "or lower(b.author) like lower(concat('%', :q, '%'))")
    Page<Book> search(@Param("q") String q, Pageable pageable);

    List<Book> findBySaleStatusOrderBySalesCountDesc(SaleStatus saleStatus, Pageable pageable);

    List<Book> findBySaleStatusOrderByPublishedDateDesc(SaleStatus saleStatus, Pageable pageable);
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.BookRepositoryTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/repository/BookRepository.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/repository/BookRepositoryTest.java
git commit -m "feat(book): BookRepository에 카테고리/검색/정렬 쿼리 추가"
```

---

### Task 3: 응답 DTO

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/dto/BookSummaryResponse.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/dto/BookDetailResponse.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/dto/BookSynopsisDetailResponse.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/dto/BookDtoTest.java`

**Interfaces:**
- Consumes: `Book`, `Book.builder()` (Task 1).
- Produces: `BookSummaryResponse.from(Book)`, `BookDetailResponse.from(Book)`, `BookSynopsisDetailResponse.from(Book)` — 각각 static factory. Task 4(Service)에서 사용.

- [ ] **Step 1: 실패하는 DTO 매핑 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/dto/BookDtoTest.java`:

```java
package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BookDtoTest {

    private Book sampleBook() {
        return Book.builder()
                .title("제목").author("저자").publisher("출판사").isbn("9791100000001")
                .category("소설").price(15000).stockQuantity(7)
                .coverImageUrl("https://example.com/cover.jpg")
                .description("짧은 소개")
                .detailedSynopsis("상세 줄거리")
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.of(2026, 5, 1))
                .salesCount(9)
                .build();
    }

    @Test
    void BookSummaryResponse는_요약_필드만_매핑한다() {
        BookSummaryResponse response = BookSummaryResponse.from(sampleBook());

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.author()).isEqualTo("저자");
        assertThat(response.price()).isEqualTo(15000);
        assertThat(response.coverImageUrl()).isEqualTo("https://example.com/cover.jpg");
        assertThat(response.category()).isEqualTo("소설");
        assertThat(response.saleStatus()).isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    void BookDetailResponse는_상세_필드를_매핑한다() {
        BookDetailResponse response = BookDetailResponse.from(sampleBook());

        assertThat(response.publisher()).isEqualTo("출판사");
        assertThat(response.isbn()).isEqualTo("9791100000001");
        assertThat(response.stockQuantity()).isEqualTo(7);
        assertThat(response.description()).isEqualTo("짧은 소개");
        assertThat(response.publishedDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void BookSynopsisDetailResponse는_상세줄거리를_매핑한다() {
        BookSynopsisDetailResponse response = BookSynopsisDetailResponse.from(sampleBook());

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.detailedSynopsis()).isEqualTo("상세 줄거리");
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.dto.BookDtoTest"`
Expected: FAIL — `BookSummaryResponse` 등 클래스가 존재하지 않아 컴파일 실패.

- [ ] **Step 3: DTO 클래스 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/dto/BookSummaryResponse.java`:

```java
package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;

public record BookSummaryResponse(
        Long id,
        String title,
        String author,
        int price,
        String coverImageUrl,
        String category,
        SaleStatus saleStatus
) {
    public static BookSummaryResponse from(Book book) {
        return new BookSummaryResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPrice(),
                book.getCoverImageUrl(),
                book.getCategory(),
                book.getSaleStatus()
        );
    }
}
```

`backend/modules/book/src/main/java/com/bookeatinglion/book/dto/BookDetailResponse.java`:

```java
package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookDetailResponse(
        Long id,
        String title,
        String author,
        String publisher,
        String isbn,
        String category,
        int price,
        int stockQuantity,
        String coverImageUrl,
        String description,
        SaleStatus saleStatus,
        LocalDate publishedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BookDetailResponse from(Book book) {
        return new BookDetailResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getIsbn(),
                book.getCategory(),
                book.getPrice(),
                book.getStockQuantity(),
                book.getCoverImageUrl(),
                book.getDescription(),
                book.getSaleStatus(),
                book.getPublishedDate(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}
```

`backend/modules/book/src/main/java/com/bookeatinglion/book/dto/BookSynopsisDetailResponse.java`:

```java
package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;

public record BookSynopsisDetailResponse(
        Long bookId,
        String title,
        String detailedSynopsis
) {
    public static BookSynopsisDetailResponse from(Book book) {
        return new BookSynopsisDetailResponse(
                book.getId(),
                book.getTitle(),
                book.getDetailedSynopsis()
        );
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.dto.BookDtoTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/dto \
        backend/modules/book/src/test/java/com/bookeatinglion/book/dto
git commit -m "feat(book): 목록/상세/상세줄거리 응답 DTO 추가"
```

---

### Task 4: BookNotFoundException + BookService

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/exception/BookNotFoundException.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/service/BookService.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/service/BookServiceTest.java`

**Interfaces:**
- Consumes: `BookRepository`(Task 2), `BookSummaryResponse.from`/`BookDetailResponse.from`/`BookSynopsisDetailResponse.from`(Task 3), `Book.builder()`(Task 1).
- Produces: `BookService`의 `getBooks(String category, Pageable pageable): Page<BookSummaryResponse>`, `search(String q, Pageable pageable): Page<BookSummaryResponse>`, `getBook(Long bookId): BookDetailResponse`, `getBestsellers(int limit): List<BookSummaryResponse>`, `getNewReleases(int limit): List<BookSummaryResponse>`, `getSynopsisDetail(Long bookId): BookSynopsisDetailResponse`. `BookNotFoundException(Long bookId)`. Task 5(Controller)에서 사용.

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/service/BookServiceTest.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    private Book book(Long id, String title) throws Exception {
        Book book = Book.builder()
                .title(title).author("저자").publisher("출판사").isbn("978110000" + id)
                .category("소설").price(10000).stockQuantity(5)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.of(2026, 1, 1)).salesCount(0)
                .build();
        Field idField = findIdField(Book.class);
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
    }

    private Field findIdField(Class<?> type) throws NoSuchFieldException {
        return type.getDeclaredField("id");
    }

    @Test
    void 카테고리가_없으면_전체_목록을_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Book> page = new PageImpl<>(List.of(book(1L, "책1")));
        when(bookRepository.findAll(pageable)).thenReturn(page);

        Page<BookSummaryResponse> result = bookService.getBooks(null, pageable);

        assertThat(result.getContent()).extracting(BookSummaryResponse::title).containsExactly("책1");
    }

    @Test
    void 카테고리가_있으면_필터링해서_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Book> page = new PageImpl<>(List.of(book(1L, "소설책")));
        when(bookRepository.findByCategory(eq("소설"), any())).thenReturn(page);

        Page<BookSummaryResponse> result = bookService.getBooks("소설", pageable);

        assertThat(result.getContent()).extracting(BookSummaryResponse::title).containsExactly("소설책");
    }

    @Test
    void 존재하는_책_id로_상세조회한다() throws Exception {
        Book book = book(1L, "상세책");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookDetailResponse result = bookService.getBook(1L);

        assertThat(result.title()).isEqualTo("상세책");
    }

    @Test
    void 존재하지_않는_책_id는_예외를_던진다() {
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBook(999L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 베스트셀러를_조회한다() throws Exception {
        when(bookRepository.findBySaleStatusOrderBySalesCountDesc(eq(SaleStatus.ON_SALE), any()))
                .thenReturn(List.of(book(1L, "베스트셀러책")));

        List<BookSummaryResponse> result = bookService.getBestsellers(10);

        assertThat(result).extracting(BookSummaryResponse::title).containsExactly("베스트셀러책");
    }

    @Test
    void 신간을_조회한다() throws Exception {
        when(bookRepository.findBySaleStatusOrderByPublishedDateDesc(eq(SaleStatus.ON_SALE), any()))
                .thenReturn(List.of(book(1L, "신간책")));

        List<BookSummaryResponse> result = bookService.getNewReleases(10);

        assertThat(result).extracting(BookSummaryResponse::title).containsExactly("신간책");
    }

    @Test
    void 존재하는_책의_상세줄거리를_조회한다() throws Exception {
        Book book = book(1L, "줄거리책");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookSynopsisDetailResponse result = bookService.getSynopsisDetail(1L);

        assertThat(result.title()).isEqualTo("줄거리책");
    }

    @Test
    void 존재하지_않는_책의_상세줄거리_조회는_예외를_던진다() {
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getSynopsisDetail(999L))
                .isInstanceOf(BookNotFoundException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.BookServiceTest"`
Expected: FAIL — `BookService`, `BookNotFoundException` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: BookNotFoundException 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/exception/BookNotFoundException.java`:

```java
package com.bookeatinglion.book.exception;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long bookId) {
        super("Book not found: id=" + bookId);
    }
}
```

- [ ] **Step 4: BookService 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/service/BookService.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;

    public Page<BookSummaryResponse> getBooks(String category, Pageable pageable) {
        Page<Book> books = (category == null || category.isBlank())
                ? bookRepository.findAll(pageable)
                : bookRepository.findByCategory(category, pageable);
        return books.map(BookSummaryResponse::from);
    }

    public Page<BookSummaryResponse> search(String q, Pageable pageable) {
        return bookRepository.search(q, pageable).map(BookSummaryResponse::from);
    }

    public BookDetailResponse getBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        return BookDetailResponse.from(book);
    }

    public List<BookSummaryResponse> getBestsellers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return bookRepository.findBySaleStatusOrderBySalesCountDesc(SaleStatus.ON_SALE, pageable)
                .stream()
                .map(BookSummaryResponse::from)
                .toList();
    }

    public List<BookSummaryResponse> getNewReleases(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return bookRepository.findBySaleStatusOrderByPublishedDateDesc(SaleStatus.ON_SALE, pageable)
                .stream()
                .map(BookSummaryResponse::from)
                .toList();
    }

    public BookSynopsisDetailResponse getSynopsisDetail(Long bookId) {
        // TODO: order 모듈 완성 후 구매 인증(해당 회원의 구매 이력) 검증 로직 추가 필요.
        // 현재는 order 모듈에 구매 이력 조회 기능이 없어 인증 체크 없이 상세줄거리를 반환한다.
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        return BookSynopsisDetailResponse.from(book);
    }
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.BookServiceTest"`
Expected: PASS (8 tests)

- [ ] **Step 6: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/exception \
        backend/modules/book/src/main/java/com/bookeatinglion/book/service \
        backend/modules/book/src/test/java/com/bookeatinglion/book/service
git commit -m "feat(book): BookService 구현 (목록/검색/상세/베스트셀러/신간/상세줄거리)"
```

---

### Task 5: BookController + 예외 핸들러

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookController.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/controller/BookControllerTest.java`

**Interfaces:**
- Consumes: `BookService`(Task 4), `BookNotFoundException`(Task 4), `com.bookeatinglion.common.dto.ApiResponse`(기존), `BookModuleTestApplication`(Task 1).
- Produces: `GET /api/books`, `GET /api/books/search`, `GET /api/books/bestsellers`, `GET /api/books/new-releases`, `GET /api/books/{bookId}`, `GET /api/books/{bookId}/synopsis/detail`.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/controller/BookControllerTest.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    private BookSummaryResponse summary(Long id, String title) {
        return new BookSummaryResponse(id, title, "저자", 10000, "cover.jpg", "소설", SaleStatus.ON_SALE);
    }

    @Test
    void 목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(bookService.getBooks(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L, "책1")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("책1"));
    }

    @Test
    void 검색은_200과_데이터를_반환한다() throws Exception {
        when(bookService.search(eq("스프링"), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L, "스프링 입문")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/books/search").param("q", "스프링"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("스프링 입문"));
    }

    @Test
    void 베스트셀러는_200과_리스트를_반환한다() throws Exception {
        when(bookService.getBestsellers(anyInt())).thenReturn(List.of(summary(1L, "베스트셀러책")));

        mockMvc.perform(get("/api/books/bestsellers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("베스트셀러책"));
    }

    @Test
    void 신간은_200과_리스트를_반환한다() throws Exception {
        when(bookService.getNewReleases(anyInt())).thenReturn(List.of(summary(1L, "신간책")));

        mockMvc.perform(get("/api/books/new-releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("신간책"));
    }

    @Test
    void 상세_조회는_200과_데이터를_반환한다() throws Exception {
        BookDetailResponse detail = new BookDetailResponse(
                1L, "상세책", "저자", "출판사", "9791100000001", "소설", 10000, 5,
                "cover.jpg", "설명", SaleStatus.ON_SALE, LocalDate.of(2026, 1, 1),
                LocalDateTime.now(), LocalDateTime.now());
        when(bookService.getBook(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("상세책"));
    }

    @Test
    void 존재하지_않는_책_상세조회는_404를_반환한다() throws Exception {
        when(bookService.getBook(999L)).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/books/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 상세줄거리_조회는_200과_데이터를_반환한다() throws Exception {
        when(bookService.getSynopsisDetail(1L))
                .thenReturn(new BookSynopsisDetailResponse(1L, "책제목", "상세 줄거리 본문"));

        mockMvc.perform(get("/api/books/1/synopsis/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detailedSynopsis").value("상세 줄거리 본문"));
    }

    @Test
    void 존재하지_않는_책의_상세줄거리_조회는_404를_반환한다() throws Exception {
        when(bookService.getSynopsisDetail(999L)).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/books/999/synopsis/detail"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.BookControllerTest"`
Expected: FAIL — `BookController`, `BookExceptionHandler` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: BookController 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookController.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.service.BookService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping
    public ApiResponse<Page<BookSummaryResponse>> getBooks(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(bookService.getBooks(category, pageable));
    }

    @GetMapping("/search")
    public ApiResponse<Page<BookSummaryResponse>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(bookService.search(q, pageable));
    }

    @GetMapping("/bestsellers")
    public ApiResponse<List<BookSummaryResponse>> getBestsellers(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(bookService.getBestsellers(limit));
    }

    @GetMapping("/new-releases")
    public ApiResponse<List<BookSummaryResponse>> getNewReleases(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(bookService.getNewReleases(limit));
    }

    @GetMapping("/{bookId}")
    public ApiResponse<BookDetailResponse> getBook(@PathVariable Long bookId) {
        return ApiResponse.success(bookService.getBook(bookId));
    }

    @GetMapping("/{bookId}/synopsis/detail")
    public ApiResponse<BookSynopsisDetailResponse> getSynopsisDetail(@PathVariable Long bookId) {
        return ApiResponse.success(bookService.getSynopsisDetail(bookId));
    }
}
```

- [ ] **Step 4: BookExceptionHandler 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bookeatinglion.book.controller")
public class BookExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookNotFound(BookNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.BookControllerTest"`
Expected: PASS (8 tests)

- [ ] **Step 6: book 모듈 전체 테스트 실행**

Run: `./gradlew :modules:book:test`
Expected: PASS (전체 — BookPersistenceTest 2, BookRepositoryTest 4, BookDtoTest 3, BookServiceTest 8, BookControllerTest 8)

- [ ] **Step 7: book 모듈 빌드 확인**

Run: `./gradlew :modules:book:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/controller \
        backend/modules/book/src/test/java/com/bookeatinglion/book/controller
git commit -m "feat(book): BookController API 6종 구현 (목록/검색/상세/베스트셀러/신간/상세줄거리)"
```

---

## Out of Scope (구현하지 않음)

- 실제 판매량(`salesCount`) 반영 로직 — 주문 완료 시 증가시키는 로직은 `order` 모듈 작업 범위
- `/synopsis/detail`의 구매 인증 검증 — TODO 주석만 남김
- `member` 모듈의 기존 컴파일 에러(`MemberService.findByUsername`) — book 도메인과 무관, 별도 이슈
- `common.response.ApiResponse` 정리, `CardStatus` enum 정리
- Flyway 마이그레이션 파일 추가
