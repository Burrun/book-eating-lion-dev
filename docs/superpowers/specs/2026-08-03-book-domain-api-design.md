# Book 도메인 API 설계

- Date: 2026-08-03
- Scope: `backend/modules/book`, `backend/modules/common`(참조만), `db/1_demo_data.sql`
- Status: Approved

## 배경

`modules/book`의 `Book` 엔티티는 현재 `title`, `price` 필드만 가지고 있고, 도서 목록/검색/상세/베스트셀러/신간 API가 전혀 구현되어 있지 않다. 프로젝트에는 ERD나 API 명세 문서가 없고, `db/1_demo_data.sql`에도 최소 스키마(`id, title, price, created_at, updated_at`)만 존재한다. 이 문서는 일반적인 서점 도메인 기준으로 스키마를 확장하고, 5개의 조회 API + 상세줄거리 API를 추가하기 위한 설계다.

## 조사 결과 (기존 코드 확인)

- `common` 모듈에 `ApiResponse` 클래스가 두 개 존재한다: `common.dto.ApiResponse`(`success/error` 정적 메서드)와 `common.response.ApiResponse`(`ok/error` 정적 메서드). 둘 다 프로젝트 전체에서 아직 사용되지 않았다(컨트롤러가 하나도 없었음). → **`common.dto.ApiResponse`를 표준으로 채택**. `common.response.ApiResponse`는 이번 범위에서 건드리지 않는다(사용자 확인 완료, 필요 시 추후 별도 정리).
- `book.domain`에 `SaleStatus`(ON_SALE/STOPPED/OUT_OF_STOCK) enum이 이미 존재하며 미사용 상태다 → 이번에 `Book.saleStatus` 필드로 재사용한다.
- `book.domain`에 `CardStatus`(ACTIVE/SUSPENDED/TERMINATED) enum도 존재하지만 book 도메인과 무관해 보인다. 이번 작업 범위가 아니므로 건드리지 않는다.
- `order` 모듈이 `book` 모듈에 의존하는 구조라서 `book`에서 `order`를 참조할 수 없다(순환 의존 불가). 따라서 베스트셀러를 실시간 주문 집계로 계산할 수 없다 → `Book.salesCount`를 비정규화 필드로 추가하고 정렬 기준으로 사용한다. 실제 판매량 반영(주문 완료 시 증가 등)은 이번 범위 밖이다.
- `application-local.yml`에 `spring.jpa.hibernate.ddl-auto: update`가 설정되어 있고, 프로젝트 어디에도 Flyway 마이그레이션 파일이 없다(의존성만 추가되어 있음) → 이번에도 마이그레이션 파일을 추가하지 않고 기존 관례를 따른다.
- 프로젝트 전체에 컨트롤러가 하나도 없어 API 스타일에 대한 기존 관례가 없다. Spring Data `Pageable`(page/size/sort 쿼리 파라미터) 기반의 표준적인 페이징을 채택한다.

## 1. Book 엔티티 & DB 스키마 변경

`modules/book/src/main/java/com/bookeatinglion/book/domain/Book.java`:

| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | Long | PK, AUTO_INCREMENT | 기존 |
| title | String | NOT NULL | 기존 |
| author | String | NOT NULL | 신규 |
| publisher | String | NOT NULL | 신규 |
| isbn | String(20) | NOT NULL, UNIQUE | 신규 |
| category | String | NOT NULL | 신규, 자유 문자열 (enum 아님) |
| price | int | NOT NULL | 기존 |
| stockQuantity | int | NOT NULL, default 0 | 신규 |
| coverImageUrl | String | NULL 허용 | 신규 |
| description | String (TEXT) | NULL 허용 | 신규, 목록/상세용 짧은 소개 |
| detailedSynopsis | String (TEXT) | NULL 허용 | 신규, `/synopsis/detail` 전용 상세줄거리(스포일러 포함 가능) |
| saleStatus | SaleStatus (enum) | NOT NULL, default ON_SALE | 기존 enum 재사용 |
| publishedDate | LocalDate | NOT NULL | 신규, 신간 정렬 기준 |
| salesCount | int | NOT NULL, default 0 | 신규, 베스트셀러 정렬 기준 (비정규화) |
| createdAt/updatedAt | LocalDateTime | BaseEntity 상속 | 기존 |

`db/1_demo_data.sql`의 `books` `CREATE TABLE`과 `INSERT`문을 위 스키마에 맞게 갱신한다(NOT NULL 컬럼이 늘어나므로 기존 데모 INSERT가 깨지지 않도록).

## 2. DTO (`book.dto` 패키지, Java record)

- `BookSummaryResponse`: id, title, author, price, coverImageUrl, category, saleStatus — 목록/검색/베스트셀러/신간 공용
- `BookDetailResponse`: id, title, author, publisher, isbn, category, price, stockQuantity, coverImageUrl, description, saleStatus, publishedDate, createdAt, updatedAt — `/api/books/{bookId}` 전용 (detailedSynopsis는 미포함, 별도 엔드포인트)
- `BookSynopsisDetailResponse`: bookId, title, detailedSynopsis — `/api/books/{bookId}/synopsis/detail` 전용

각 DTO는 정적 팩토리 메서드(`from(Book book)`)로 엔티티를 변환한다.

## 3. Repository (`BookRepository`)

```java
Page<Book> findByCategory(String category, Pageable pageable);

@Query("select b from Book b where lower(b.title) like lower(concat('%', :q, '%')) " +
       "or lower(b.author) like lower(concat('%', :q, '%'))")
Page<Book> search(@Param("q") String q, Pageable pageable);

List<Book> findBySaleStatusOrderBySalesCountDesc(SaleStatus saleStatus, Pageable pageable);

List<Book> findBySaleStatusOrderByPublishedDateDesc(SaleStatus saleStatus, Pageable pageable);
```

베스트셀러/신간은 `ON_SALE` 상태인 책만 대상으로 하고, `limit`은 `PageRequest.of(0, limit)`으로 전달한다.

## 4. Service (`BookService`, `@Service @RequiredArgsConstructor @Transactional(readOnly = true)`)

- `getBooks(String category, Pageable pageable): Page<BookSummaryResponse>` — category가 null이면 전체 조회, 있으면 필터링
- `search(String q, Pageable pageable): Page<BookSummaryResponse>`
- `getBook(Long bookId): BookDetailResponse` — 없으면 `BookNotFoundException`
- `getBestsellers(int limit): List<BookSummaryResponse>`
- `getNewReleases(int limit): List<BookSummaryResponse>`
- `getSynopsisDetail(Long bookId): BookSynopsisDetailResponse` — 없으면 `BookNotFoundException`.
  ```java
  // TODO: order 모듈 완성 후 구매 인증(해당 회원의 구매 이력) 검증 로직 추가 필요.
  // 현재는 order 모듈에 구매 이력 조회 기능이 없어 인증 체크 없이 상세줄거리를 반환한다.
  ```

## 5. Controller (`BookController`, `@RestController @RequestMapping("/api/books")`)

```
GET /api/books?category=&page=0&size=20&sort=
GET /api/books/search?q=&page=0&size=20
GET /api/books/{bookId}
GET /api/books/{bookId}/synopsis/detail
GET /api/books/bestsellers?limit=10
GET /api/books/new-releases?limit=10
```

모든 응답은 `ApiResponse<T>`(`common.dto.ApiResponse`)로 감싼다. `search`/`bestsellers`/`new-releases`/`{bookId}/synopsis/detail`은 리터럴 경로 세그먼트를 포함하므로 `{bookId}` 단독 패턴과 라우팅 충돌이 없다.

## 6. 예외 처리

- `book.exception.BookNotFoundException`(RuntimeException) 추가
- `book.controller` 하위에 `@RestControllerAdvice`(book 모듈 범위 한정)를 추가해 `BookNotFoundException` → HTTP 404 + `ApiResponse.error(...)`로 변환. 프로젝트 전체 공통 예외 처리 체계는 아직 없으므로 이번엔 book 모듈 범위로 국한한다.

## 7. 테스트

- `BookRepositoryTest`(`@DataJpaTest`): category 필터, search(title/author), salesCount/publishedDate 정렬 검증
- `BookControllerTest`(`@WebMvcTest` + `MockMvc`, `BookService` mock): 5개 엔드포인트 + synopsis/detail 엔드포인트의 골든 패스, 존재하지 않는 bookId에 대한 404 케이스 검증

## 비범위 (Out of scope)

- 실제 판매량 반영 로직(주문 완료 시 `salesCount` 증가 등)
- 상세줄거리 구매 인증 검증 로직 (TODO로 남김)
- `common.response.ApiResponse` 정리, `CardStatus` enum 정리
- Flyway 마이그레이션 파일 추가
