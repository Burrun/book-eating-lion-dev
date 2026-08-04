# Book 도메인 리뷰/찜/최근 본 상품 API 설계 (BOO-7)

- Date: 2026-08-03
- Scope: `backend/modules/book` (신규 모듈 생성 없이 기존 book 모듈 확장), `db/1_demo_data.sql`
- Status: Approved
- 선행 작업: BOO-6 (`docs/superpowers/specs/2026-08-03-book-domain-api-design.md`)

## 배경

BOO-6에서 만든 `Book` 엔티티, `BookRepository`, `common.dto.ApiResponse` 응답 포맷을 이어서, 리뷰/찜/최근 본 상품 API를 구현한다. `Member`는 아직 `id, email, name`만 있는 최소 스켈레톤이고 프로젝트 전체에 인증 인프라(SecurityFilterChain, JWT 등)가 없으므로, 새 엔티티는 `Book`은 `@ManyToOne`으로 완전히 참조하고 `Member`는 `memberId(Long)` 필드로만 느슨하게 연결한다.

## 조사 및 결정 사항 (사용자 확인 완료)

- **"나(me)" 식별**: 인증 인프라가 없으므로 요청 헤더 `X-Member-Id`를 임시 신원 확인 수단으로 사용한다. `@RequestHeader("X-Member-Id") Long memberId`를 각 컨트롤러 메서드에 직접 선언(별도 리졸버/애노테이션 없음, YAGNI). 헤더가 없으면 Spring 기본 동작으로 400이 반환된다. 실제 인증(JWT 등) 도입 시 이 부분만 교체하면 된다.
- **최근 본 상품 기록 시점**: 별도 기록용 엔드포인트를 추가하지 않고, BOO-6의 `GET /api/books/{bookId}`(상세조회)에 기록 로직을 훅으로 추가한다. 이 엔드포인트는 `X-Member-Id`가 `required = false`로, 헤더가 있을 때만 기록한다(기존 공개 조회 동작은 그대로 유지).
- **Review 필드**: `rating`(1~5 int, 필수) + `content`(String, 필수).
- **범위**: 새 Gradle 모듈을 만들지 않고 기존 `modules/book`(`com.bookeatinglion.book.*`) 안에 패키지를 추가한다. `Member`를 느슨하게만 참조하므로 book 모듈이 `modules:member`에 의존할 필요가 없다(BOO-6에서 정리했던 불필요한 의존성 문제를 재발시키지 않음).

## 비즈니스 규칙

- **찜(Wishlist) POST/DELETE는 멱등**이다. 이미 찜한 책을 다시 `POST`하면 에러 없이 기존 항목을 유지하고 200을 반환한다. 찜하지 않은 책을 `DELETE`해도 에러 없이 200을 반환한다.
- **리뷰 삭제는 작성자 본인만 가능**하다. `X-Member-Id`가 리뷰의 `memberId`와 다르면 403(`ReviewAccessDeniedException`).
- **최근 본 상품은 upsert**된다. 같은 회원이 같은 책을 다시 보면 새 행을 만들지 않고 기존 행의 `viewedAt`만 갱신한다(도메인 메서드로 touch). 목록은 `viewedAt` 내림차순 + `limit` 쿼리 파라미터(기본 20 — BOO-6의 베스트셀러/신간과 동일 패턴)로 조회한다.

## 1. 엔티티

`backend/modules/book/src/main/java/com/bookeatinglion/book/domain/`:

```java
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {
    Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "book_id", nullable = false) Book book;
    @Column(nullable = false) Long memberId;
    @Column(nullable = false) int rating;       // 1~5
    @Column(nullable = false, columnDefinition = "TEXT") String content;
}

@Entity
@Table(name = "wishlists", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
public class Wishlist extends BaseEntity {
    Long id;
    @Column(name = "member_id", nullable = false) Long memberId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "book_id", nullable = false) Book book;
}

@Entity
@Table(name = "recent_viewed_books", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
public class RecentViewedBook extends BaseEntity {
    Long id;
    @Column(name = "member_id", nullable = false) Long memberId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "book_id", nullable = false) Book book;
    @Column(nullable = false) LocalDateTime viewedAt;

    public void touch(LocalDateTime now) { this.viewedAt = now; }  // upsert 시 사용
}
```

## 2. DTO

- 신규(`book.dto`): `ReviewRequest`(rating, content — `@Min(1) @Max(5)`, `@NotBlank` 검증), `ReviewResponse`(id, bookId, memberId, rating, content, createdAt)
- **재사용**: `GET /api/members/me/wishlist`, `GET /api/members/me/recent-books`는 BOO-6의 `BookSummaryResponse` 리스트로 응답한다(새 DTO 생성 안 함).

## 3. Repository

```java
ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByBookId(Long bookId, Pageable pageable);
}

WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByMemberIdAndBookId(Long memberId, Long bookId);
    List<Wishlist> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    void deleteByMemberIdAndBookId(Long memberId, Long bookId);
}

RecentViewedBookRepository extends JpaRepository<RecentViewedBook, Long> {
    Optional<RecentViewedBook> findByMemberIdAndBookId(Long memberId, Long bookId);
    List<RecentViewedBook> findByMemberIdOrderByViewedAtDesc(Long memberId, Pageable pageable);
}
```

## 4. Service

- `ReviewService`
  - `getReviews(Long bookId, Pageable pageable): Page<ReviewResponse>` — book 존재 검증(`BookNotFoundException` 재사용)
  - `createReview(Long bookId, Long memberId, ReviewRequest request): ReviewResponse`
  - `deleteReview(Long reviewId, Long memberId)` — 없으면 `ReviewNotFoundException`, 작성자 불일치면 `ReviewAccessDeniedException`
- `WishlistService`
  - `addWishlist(Long bookId, Long memberId)` — 존재하면 멱등하게 통과, 없으면 book 존재 검증 후 생성
  - `removeWishlist(Long bookId, Long memberId)` — 존재 여부 상관없이 삭제 시도(멱등)
  - `getMyWishlist(Long memberId): List<BookSummaryResponse>`
- `RecentViewedBookService`
  - `recordView(Long bookId, Long memberId)` — 있으면 touch, 없으면 생성
  - `getMyRecentBooks(Long memberId, int limit): List<BookSummaryResponse>`

## 5. Controller

```
GET    /api/books/{bookId}/reviews          ReviewController
POST   /api/books/{bookId}/reviews          ReviewController  (X-Member-Id 필수, 201)
DELETE /api/reviews/{reviewId}               ReviewController  (X-Member-Id 필수, 200)

POST   /api/wishlist/{bookId}                WishlistController (X-Member-Id 필수, 200, 멱등)
DELETE /api/wishlist/{bookId}                WishlistController (X-Member-Id 필수, 200, 멱등)

GET    /api/members/me/wishlist              MemberBookQueryController (X-Member-Id 필수)
GET    /api/members/me/recent-books          MemberBookQueryController (X-Member-Id 필수, limit 기본 20)
```

(사용자 요청으로 `MemberQueryController` → `MemberBookQueryController`로 확정 — book 모듈 안에 있다는 점이 이름에서 드러나도록.)

기존 `BookController.getBook(bookId, @RequestHeader(value = "X-Member-Id", required = false) Long memberId)`에 `recentViewedBookService.recordView(bookId, memberId)` 호출을 memberId가 null이 아닐 때만 추가한다.

## 6. 예외 처리

기존 `BookExceptionHandler`(`@RestControllerAdvice(basePackages = "com.bookeatinglion.book.controller")`)에 핸들러 추가:
- `ReviewNotFoundException` → 404
- `ReviewAccessDeniedException` → 403

Wishlist는 멱등 정책이라 별도 예외 없음.

## 7. 데모 SQL

`db/1_demo_data.sql`에 `reviews`, `wishlists`, `recent_viewed_books` `CREATE TABLE` 추가(BOO-6와 동일 컨벤션: `IF NOT EXISTS`, `created_at`/`updated_at` 포함).

## 8. 테스트

각 계층 TDD로 진행하며, 기존 `BookModuleTestApplication`(같은 패키지 트리 내라 별도 설정 불필요)을 그대로 재사용한다.

## 비범위 (Out of scope)

- 실제 인증/인가(JWT 등) 도입 — `X-Member-Id` 헤더는 임시 수단
- 리뷰 수정(PATCH/PUT), 평점 집계(평균 별점 등 Book 엔티티 반영)
- 최근 본 상품 목록 상한(오래된 행 정리) — 무제한 누적, 조회 시 `limit`으로만 제어
- Member 존재 여부 검증 (memberId는 느슨한 참조라 FK 제약 없음)
