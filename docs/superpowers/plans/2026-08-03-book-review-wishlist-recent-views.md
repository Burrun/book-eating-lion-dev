# Book 도메인 리뷰/찜/최근 본 상품 API Implementation Plan (BOO-7)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** BOO-6의 `Book`/`BookRepository`/`common.dto.ApiResponse`를 이어서, 리뷰/찜/최근 본 상품 API를 기존 `modules/book` 안에 domain/repository/service/dto/controller 계층으로 구현한다.

**Architecture:** `Review`/`Wishlist`/`RecentViewedBook`은 `Book`을 `@ManyToOne`으로 완전히 참조하고 `Member`는 `memberId(Long)`로만 느슨하게 참조한다. 인증 인프라가 없으므로 `X-Member-Id` 요청 헤더를 임시 신원 확인 수단으로 사용한다.

**Tech Stack:** BOO-6와 동일 (Spring Boot 3.4.2, Java 21, Spring Data JPA, Lombok, JUnit 5 + Mockito + MockMvc, H2, Gradle 8.12)

**Reference:** 설계 문서 `docs/superpowers/specs/2026-08-03-book-review-wishlist-recent-views-design.md` (사용자 승인 완료), 선행 작업 `docs/superpowers/plans/2026-08-03-book-domain-api.md`

## Global Constraints

- 모든 응답은 `com.bookeatinglion.common.dto.ApiResponse<T>`로 감싼다 (BOO-6와 동일).
- 새 Gradle 모듈을 만들지 않는다. 모든 코드는 `backend/modules/book`의 `com.bookeatinglion.book.*` 패키지에 추가한다. book 모듈은 `modules:member`에 의존하지 않는다(memberId는 Long 필드로만 연결).
- 인증이 필요한 모든 엔드포인트는 `@RequestHeader("X-Member-Id") Long memberId`로 회원을 식별한다. `GET /api/books/{bookId}`(BOO-6)만 `required = false`로, 헤더가 있을 때만 최근 본 상품을 기록한다.
- 찜(Wishlist) `POST`/`DELETE`는 멱등이다(이미 있어도/없어도 에러 없이 200).
- 리뷰 삭제는 작성자 본인만 가능하다(`X-Member-Id` != 작성자면 403).
- 최근 본 상품은 회원-책 조합당 한 행만 유지하고(upsert), 다시 보면 `viewedAt`만 갱신한다.
- 모든 명령은 `backend` 디렉터리에서 `./gradlew`로 실행하고, `:modules:book:test`/`:modules:book:build` 태스크를 사용한다(루트 전체 빌드는 `member` 모듈의 기존 무관한 버그로 여전히 실패함 — BOO-6 계획 문서 참고).
- IDENTITY 생성 전략 엔티티의 유니크 제약 테스트는 `assertThrows`를 `save()`/`persist()` 호출 자체에 걸어야 한다(`flush()`가 아님) — BOO-6에서 확인된 사항.

---

## File Structure

```
backend/modules/book/src/main/java/com/bookeatinglion/book/
  domain/Review.java                          (신규)
  domain/Wishlist.java                        (신규)
  domain/RecentViewedBook.java                (신규)
  repository/ReviewRepository.java            (신규)
  repository/WishlistRepository.java          (신규)
  repository/RecentViewedBookRepository.java  (신규)
  dto/ReviewRequest.java                      (신규)
  dto/ReviewResponse.java                     (신규)
  exception/ReviewNotFoundException.java      (신규)
  exception/ReviewAccessDeniedException.java  (신규)
  service/ReviewService.java                  (신규)
  service/WishlistService.java                (신규)
  service/RecentViewedBookService.java        (신규)
  controller/ReviewController.java            (신규)
  controller/WishlistController.java          (신규)
  controller/MemberBookQueryController.java   (신규)
  controller/BookController.java              (수정: X-Member-Id 훅 추가)
  controller/BookExceptionHandler.java        (수정: Review 예외/검증 예외 핸들러 추가)

backend/modules/book/src/test/java/com/bookeatinglion/book/
  repository/ReviewRepositoryTest.java            (신규)
  dto/ReviewDtoTest.java                          (신규)
  service/ReviewServiceTest.java                  (신규)
  controller/ReviewControllerTest.java            (신규)
  repository/WishlistRepositoryTest.java          (신규)
  service/WishlistServiceTest.java                (신규)
  controller/WishlistControllerTest.java          (신규)
  repository/RecentViewedBookRepositoryTest.java  (신규)
  service/RecentViewedBookServiceTest.java        (신규)
  controller/BookControllerTest.java              (수정: RecentViewedBookService mock 추가)
  controller/MemberBookQueryControllerTest.java   (신규)

db/1_demo_data.sql  (수정: reviews/wishlists/recent_viewed_books 테이블 추가)
```

---

### Task 1: Review 엔티티 + ReviewRepository + 데모 SQL

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Review.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/repository/ReviewRepository.java`
- Modify: `db/1_demo_data.sql`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/repository/ReviewRepositoryTest.java`

**Interfaces:**
- Consumes: `Book`, `Book.builder()`, `BookRepository`, `BookModuleTestApplication` (BOO-6).
- Produces: `Review` — getter: `getId()`, `getBook()`, `getMemberId()`, `getRating()`, `getContent()`. `Review.builder().book(Book).memberId(Long).rating(int).content(String).build()`. `ReviewRepository.findByBookId(Long bookId, Pageable pageable): Page<Review>`. Task 2~4에서 사용.

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/repository/ReviewRepositoryTest.java`:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ReviewRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Book book;

    @BeforeEach
    void setUp() {
        book = bookRepository.save(Book.builder()
                .title("리뷰용 책").author("저자").publisher("출판사").isbn("9791100000021")
                .category("소설").price(10000).stockQuantity(10)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build());
    }

    @Test
    void 리뷰를_저장하고_조회한다() {
        Review review = reviewRepository.save(Review.builder()
                .book(book).memberId(1L).rating(5).content("최고의 책입니다").build());

        Review found = reviewRepository.findById(review.getId()).orElseThrow();

        assertThat(found.getBook().getId()).isEqualTo(book.getId());
        assertThat(found.getMemberId()).isEqualTo(1L);
        assertThat(found.getRating()).isEqualTo(5);
        assertThat(found.getContent()).isEqualTo("최고의 책입니다");
    }

    @Test
    void 책_id로_리뷰_목록을_페이징_조회한다() {
        reviewRepository.save(Review.builder().book(book).memberId(1L).rating(5).content("리뷰1").build());
        reviewRepository.save(Review.builder().book(book).memberId(2L).rating(3).content("리뷰2").build());

        Page<Review> result = reviewRepository.findByBookId(book.getId(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Review::getContent)
                .containsExactlyInAnyOrder("리뷰1", "리뷰2");
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.ReviewRepositoryTest"`
Expected: FAIL — `Review`, `ReviewRepository` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: Review 엔티티 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Review.java`:

```java
package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    public Review(Book book, Long memberId, int rating, String content) {
        this.book = book;
        this.memberId = memberId;
        this.rating = rating;
        this.content = content;
    }
}
```

- [ ] **Step 4: ReviewRepository 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/repository/ReviewRepository.java`:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByBookId(Long bookId, Pageable pageable);
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.ReviewRepositoryTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: 데모 SQL에 reviews 테이블 추가**

`db/1_demo_data.sql` 파일 끝에 다음을 추가한다 (기존 내용은 그대로 유지):

```sql

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

- [ ] **Step 7: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Review.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/repository/ReviewRepository.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/repository/ReviewRepositoryTest.java \
        db/1_demo_data.sql
git commit -m "feat(book): Review 엔티티 및 ReviewRepository 추가"
```

---

### Task 2: Review DTO + 예외

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/dto/ReviewRequest.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/dto/ReviewResponse.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/exception/ReviewNotFoundException.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/exception/ReviewAccessDeniedException.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/dto/ReviewDtoTest.java`

**Interfaces:**
- Consumes: `Review`, `Review.builder()`, `Book.builder()` (Task 1).
- Produces: `ReviewRequest(int rating, String content)` — record, `@Min(1) @Max(5)`/`@NotBlank` 검증. `ReviewResponse.from(Review): ReviewResponse`. `ReviewNotFoundException(Long reviewId)`. `ReviewAccessDeniedException(Long reviewId, Long memberId)`. Task 3~4에서 사용.

- [ ] **Step 1: 실패하는 DTO 매핑 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/dto/ReviewDtoTest.java`:

```java
package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewDtoTest {

    @Test
    void ReviewResponse는_리뷰_필드를_매핑한다() throws Exception {
        Book book = Book.builder()
                .title("제목").author("저자").publisher("출판사").isbn("9791100000031")
                .category("소설").price(10000).stockQuantity(1)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        setId(book, Book.class, 10L);

        Review review = Review.builder().book(book).memberId(1L).rating(4).content("괜찮아요").build();
        setId(review, Review.class, 100L);

        ReviewResponse response = ReviewResponse.from(review);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.bookId()).isEqualTo(10L);
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.content()).isEqualTo("괜찮아요");
    }

    private void setId(Object target, Class<?> type, Long id) throws Exception {
        Field idField = type.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.dto.ReviewDtoTest"`
Expected: FAIL — `ReviewResponse` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: DTO 및 예외 클래스 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/dto/ReviewRequest.java`:

```java
package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
        @Min(1) @Max(5) int rating,
        @NotBlank String content
) {
}
```

`backend/modules/book/src/main/java/com/bookeatinglion/book/dto/ReviewResponse.java`:

```java
package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long bookId,
        Long memberId,
        int rating,
        String content,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBook().getId(),
                review.getMemberId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
```

`backend/modules/book/src/main/java/com/bookeatinglion/book/exception/ReviewNotFoundException.java`:

```java
package com.bookeatinglion.book.exception;

public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(Long reviewId) {
        super("Review not found: id=" + reviewId);
    }
}
```

`backend/modules/book/src/main/java/com/bookeatinglion/book/exception/ReviewAccessDeniedException.java`:

```java
package com.bookeatinglion.book.exception;

public class ReviewAccessDeniedException extends RuntimeException {

    public ReviewAccessDeniedException(Long reviewId, Long memberId) {
        super("Member " + memberId + " is not allowed to delete review " + reviewId);
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.dto.ReviewDtoTest"`
Expected: PASS (1 test)

- [ ] **Step 5: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/dto/ReviewRequest.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/dto/ReviewResponse.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/exception/ReviewNotFoundException.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/exception/ReviewAccessDeniedException.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/dto/ReviewDtoTest.java
git commit -m "feat(book): Review DTO 및 예외 클래스 추가"
```

---

### Task 3: ReviewService

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/service/ReviewService.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/service/ReviewServiceTest.java`

**Interfaces:**
- Consumes: `ReviewRepository`(Task 1), `BookRepository`, `Book.builder()`, `BookNotFoundException`(BOO-6), `ReviewRequest`/`ReviewResponse`/`ReviewNotFoundException`/`ReviewAccessDeniedException`(Task 2).
- Produces: `ReviewService.getReviews(Long bookId, Pageable pageable): Page<ReviewResponse>`, `createReview(Long bookId, Long memberId, ReviewRequest request): ReviewResponse`, `deleteReview(Long reviewId, Long memberId): void`. Task 4에서 사용.

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/service/ReviewServiceTest.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.ReviewRequest;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private ReviewService reviewService;

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책").author("저자").publisher("출판사").isbn("978110000" + id)
                .category("소설").price(10000).stockQuantity(5)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        setId(book, Book.class, id);
        return book;
    }

    private Review review(Long id, Book book, Long memberId) throws Exception {
        Review review = Review.builder().book(book).memberId(memberId).rating(5).content("내용").build();
        setId(review, Review.class, id);
        return review;
    }

    private void setId(Object target, Class<?> type, Long id) throws Exception {
        Field idField = type.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }

    @Test
    void 존재하는_책의_리뷰_목록을_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        Book book = book(1L);
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.findByBookId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(review(100L, book, 1L))));

        Page<ReviewResponse> result = reviewService.getReviews(1L, pageable);

        assertThat(result.getContent()).extracting(ReviewResponse::content).containsExactly("내용");
    }

    @Test
    void 존재하지_않는_책의_리뷰_목록_조회는_예외를_던진다() {
        when(bookRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.getReviews(999L, PageRequest.of(0, 10)))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 리뷰를_생성한다() throws Exception {
        Book book = book(1L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            setId(saved, Review.class, 100L);
            return saved;
        });

        ReviewResponse result = reviewService.createReview(1L, 1L, new ReviewRequest(5, "최고예요"));

        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.content()).isEqualTo("최고예요");
        assertThat(result.memberId()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_책에_리뷰_생성은_예외를_던진다() {
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(999L, 1L, new ReviewRequest(5, "내용")))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 작성자_본인이_리뷰를_삭제한다() throws Exception {
        Book book = book(1L);
        Review review = review(100L, book, 1L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(100L, 1L);

        verify(reviewRepository, times(1)).delete(review);
    }

    @Test
    void 존재하지_않는_리뷰_삭제는_예외를_던진다() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(999L, 1L))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    void 작성자가_아니면_리뷰_삭제시_예외를_던진다() throws Exception {
        Book book = book(1L);
        Review review = review(100L, book, 1L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(100L, 999L))
                .isInstanceOf(ReviewAccessDeniedException.class);
        verify(reviewRepository, never()).delete(any());
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.ReviewServiceTest"`
Expected: FAIL — `ReviewService` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: ReviewService 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/service/ReviewService.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Review;
import com.bookeatinglion.book.dto.ReviewRequest;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;

    public Page<ReviewResponse> getReviews(Long bookId, Pageable pageable) {
        if (!bookRepository.existsById(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        return reviewRepository.findByBookId(bookId, pageable).map(ReviewResponse::from);
    }

    @Transactional
    public ReviewResponse createReview(Long bookId, Long memberId, ReviewRequest request) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        Review review = Review.builder()
                .book(book)
                .memberId(memberId)
                .rating(request.rating())
                .content(request.content())
                .build();
        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public void deleteReview(Long reviewId, Long memberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        if (!review.getMemberId().equals(memberId)) {
            throw new ReviewAccessDeniedException(reviewId, memberId);
        }
        reviewRepository.delete(review);
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.ReviewServiceTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/service/ReviewService.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/service/ReviewServiceTest.java
git commit -m "feat(book): ReviewService 구현"
```

---

### Task 4: ReviewController + 예외 핸들러 확장

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/ReviewController.java`
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/controller/ReviewControllerTest.java`

**Interfaces:**
- Consumes: `ReviewService`(Task 3), `ReviewRequest`/`ReviewResponse`(Task 2), `BookNotFoundException`(BOO-6), `ReviewNotFoundException`/`ReviewAccessDeniedException`(Task 2).
- Produces: `GET/POST /api/books/{bookId}/reviews`, `DELETE /api/reviews/{reviewId}`.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/controller/ReviewControllerTest.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    private ReviewResponse response(Long id) {
        return new ReviewResponse(id, 1L, 1L, 5, "좋아요", LocalDateTime.now());
    }

    @Test
    void 리뷰_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(reviewService.getReviews(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(response(100L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/books/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("좋아요"));
    }

    @Test
    void 존재하지_않는_책의_리뷰_목록_조회는_404를_반환한다() throws Exception {
        when(reviewService.getReviews(eq(999L), any())).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/books/999/reviews"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 리뷰_생성은_201과_데이터를_반환한다() throws Exception {
        when(reviewService.createReview(eq(1L), eq(1L), any())).thenReturn(response(100L));

        mockMvc.perform(post("/api/books/1/reviews")
                        .header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TestReviewRequest(5, "좋아요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("좋아요"));
    }

    @Test
    void 평점_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/books/1/reviews")
                        .header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TestReviewRequest(6, "좋아요"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 리뷰_삭제는_200을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/reviews/100").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 존재하지_않는_리뷰_삭제는_404를_반환한다() throws Exception {
        org.mockito.Mockito.doThrow(new ReviewNotFoundException(999L))
                .when(reviewService).deleteReview(999L, 1L);

        mockMvc.perform(delete("/api/reviews/999").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 작성자가_아니면_리뷰_삭제는_403을_반환한다() throws Exception {
        org.mockito.Mockito.doThrow(new ReviewAccessDeniedException(100L, 2L))
                .when(reviewService).deleteReview(100L, 2L);

        mockMvc.perform(delete("/api/reviews/100").header("X-Member-Id", "2"))
                .andExpect(status().isForbidden());
    }

    private record TestReviewRequest(int rating, String content) {
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.ReviewControllerTest"`
Expected: FAIL — `ReviewController` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: ReviewController 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/ReviewController.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.ReviewRequest;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.service.ReviewService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/books/{bookId}/reviews")
    public ApiResponse<Page<ReviewResponse>> getReviews(
            @PathVariable Long bookId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(reviewService.getReviews(bookId, pageable));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/books/{bookId}/reviews")
    public ApiResponse<ReviewResponse> createReview(
            @PathVariable Long bookId,
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(reviewService.createReview(bookId, memberId, request));
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @PathVariable Long reviewId,
            @RequestHeader("X-Member-Id") Long memberId) {
        reviewService.deleteReview(reviewId, memberId);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 4: BookExceptionHandler에 핸들러 추가**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java` 전체를 다음으로 교체:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice(basePackages = "com.bookeatinglion.book.controller")
public class BookExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookNotFound(BookNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewNotFound(ReviewNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(ReviewAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewAccessDenied(ReviewAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.ReviewControllerTest"`
Expected: PASS (7 tests)

- [ ] **Step 6: book 모듈 전체 테스트 실행 (회귀 확인)**

Run: `./gradlew :modules:book:test`
Expected: PASS (BOO-6 25개 + Task 1~4에서 추가된 테스트 모두 통과)

- [ ] **Step 7: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/controller/ReviewController.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/controller/ReviewControllerTest.java
git commit -m "feat(book): ReviewController 구현 및 예외 핸들러 확장"
```

---

### Task 5: Wishlist 엔티티 + WishlistRepository + 데모 SQL

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Wishlist.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/repository/WishlistRepository.java`
- Modify: `db/1_demo_data.sql`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/repository/WishlistRepositoryTest.java`

**Interfaces:**
- Consumes: `Book`, `Book.builder()`, `BookRepository`, `BookModuleTestApplication` (BOO-6).
- Produces: `Wishlist` — getter: `getId()`, `getMemberId()`, `getBook()`. `Wishlist.builder().memberId(Long).book(Book).build()`. `WishlistRepository.findByMemberIdAndBookId(Long, Long): Optional<Wishlist>`, `findByMemberIdOrderByCreatedAtDesc(Long): List<Wishlist>`, `deleteByMemberIdAndBookId(Long, Long): void`. Task 6에서 사용.

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/repository/WishlistRepositoryTest.java`:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.domain.Wishlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class WishlistRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    private Book book;

    @BeforeEach
    void setUp() {
        book = bookRepository.save(Book.builder()
                .title("찜용 책").author("저자").publisher("출판사").isbn("9791100000041")
                .category("소설").price(10000).stockQuantity(10)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build());
    }

    @Test
    void 찜을_저장하고_회원_책_조합으로_조회한다() {
        wishlistRepository.save(Wishlist.builder().memberId(1L).book(book).build());

        assertThat(wishlistRepository.findByMemberIdAndBookId(1L, book.getId())).isPresent();
        assertThat(wishlistRepository.findByMemberIdAndBookId(2L, book.getId())).isEmpty();
    }

    @Test
    void 같은_회원_같은_책은_중복_찜할_수_없다() {
        wishlistRepository.save(Wishlist.builder().memberId(1L).book(book).build());

        assertThrows(Exception.class, () ->
                wishlistRepository.save(Wishlist.builder().memberId(1L).book(book).build()));
    }

    @Test
    void 회원의_찜_목록을_최신순으로_조회한다() {
        wishlistRepository.save(Wishlist.builder().memberId(1L).book(book).build());

        List<Wishlist> result = wishlistRepository.findByMemberIdOrderByCreatedAtDesc(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBook().getId()).isEqualTo(book.getId());
    }

    @Test
    void 찜을_삭제한다() {
        wishlistRepository.save(Wishlist.builder().memberId(1L).book(book).build());

        wishlistRepository.deleteByMemberIdAndBookId(1L, book.getId());

        assertThat(wishlistRepository.findByMemberIdAndBookId(1L, book.getId())).isEmpty();
    }

    @Test
    void 존재하지_않는_찜을_삭제해도_에러가_나지_않는다() {
        wishlistRepository.deleteByMemberIdAndBookId(999L, book.getId());
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.WishlistRepositoryTest"`
Expected: FAIL — `Wishlist`, `WishlistRepository` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: Wishlist 엔티티 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Wishlist.java`:

```java
package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wishlists", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wishlist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Builder
    public Wishlist(Long memberId, Book book) {
        this.memberId = memberId;
        this.book = book;
    }
}
```

- [ ] **Step 4: WishlistRepository 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/repository/WishlistRepository.java`:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByMemberIdAndBookId(Long memberId, Long bookId);

    List<Wishlist> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    void deleteByMemberIdAndBookId(Long memberId, Long bookId);
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.WishlistRepositoryTest"`
Expected: PASS (5 tests)

- [ ] **Step 6: 데모 SQL에 wishlists 테이블 추가**

`db/1_demo_data.sql` 파일 끝에 다음을 추가한다:

```sql

CREATE TABLE IF NOT EXISTS wishlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wishlists_member_book (member_id, book_id)
);
```

- [ ] **Step 7: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/domain/Wishlist.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/repository/WishlistRepository.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/repository/WishlistRepositoryTest.java \
        db/1_demo_data.sql
git commit -m "feat(book): Wishlist 엔티티 및 WishlistRepository 추가"
```

---

### Task 6: WishlistService + WishlistController

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/service/WishlistService.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/WishlistController.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/service/WishlistServiceTest.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/controller/WishlistControllerTest.java`

**Interfaces:**
- Consumes: `WishlistRepository`, `Wishlist.builder()`(Task 5), `BookRepository`, `Book.builder()`, `BookNotFoundException`, `BookSummaryResponse`(BOO-6).
- Produces: `WishlistService.addWishlist(Long bookId, Long memberId): void`, `removeWishlist(Long bookId, Long memberId): void`, `getMyWishlist(Long memberId): List<BookSummaryResponse>`. `POST/DELETE /api/wishlist/{bookId}`. Task 9에서 `getMyWishlist`를 사용.

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/service/WishlistServiceTest.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.domain.Wishlist;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private WishlistService wishlistService;

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책").author("저자").publisher("출판사").isbn("978120000" + id)
                .category("소설").price(10000).stockQuantity(5)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        Field idField = Book.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
    }

    @Test
    void 찜하지_않은_책을_찜한다() throws Exception {
        Book book = book(1L);
        when(wishlistRepository.findByMemberIdAndBookId(1L, 1L)).thenReturn(Optional.empty());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        wishlistService.addWishlist(1L, 1L);

        verify(wishlistRepository, times(1)).save(any());
    }

    @Test
    void 이미_찜한_책을_다시_찜해도_중복_저장하지_않는다() throws Exception {
        Book book = book(1L);
        when(wishlistRepository.findByMemberIdAndBookId(1L, 1L))
                .thenReturn(Optional.of(Wishlist.builder().memberId(1L).book(book).build()));

        wishlistService.addWishlist(1L, 1L);

        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_책을_찜하면_예외를_던진다() {
        when(wishlistRepository.findByMemberIdAndBookId(1L, 999L)).thenReturn(Optional.empty());
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.addWishlist(999L, 1L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 찜을_삭제한다() {
        wishlistService.removeWishlist(1L, 1L);

        verify(wishlistRepository, times(1)).deleteByMemberIdAndBookId(1L, 1L);
    }

    @Test
    void 내_찜_목록을_책_요약_정보로_조회한다() throws Exception {
        Book book = book(1L);
        when(wishlistRepository.findByMemberIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(Wishlist.builder().memberId(1L).book(book).build()));

        List<BookSummaryResponse> result = wishlistService.getMyWishlist(1L);

        assertThat(result).extracting(BookSummaryResponse::title).containsExactly("책");
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.WishlistServiceTest"`
Expected: FAIL — `WishlistService` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: WishlistService 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/service/WishlistService.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.domain.Wishlist;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final BookRepository bookRepository;

    @Transactional
    public void addWishlist(Long bookId, Long memberId) {
        if (wishlistRepository.findByMemberIdAndBookId(memberId, bookId).isPresent()) {
            return;
        }
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        wishlistRepository.save(Wishlist.builder().memberId(memberId).book(book).build());
    }

    @Transactional
    public void removeWishlist(Long bookId, Long memberId) {
        wishlistRepository.deleteByMemberIdAndBookId(memberId, bookId);
    }

    public List<BookSummaryResponse> getMyWishlist(Long memberId) {
        return wishlistRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(wishlist -> BookSummaryResponse.from(wishlist.getBook()))
                .toList();
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.WishlistServiceTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: 실패하는 컨트롤러 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/controller/WishlistControllerTest.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.service.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WishlistController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WishlistService wishlistService;

    @Test
    void 찜하기는_200을_반환한다() throws Exception {
        mockMvc.perform(post("/api/wishlist/1").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wishlistService, times(1)).addWishlist(1L, 1L);
    }

    @Test
    void 존재하지_않는_책_찜하기는_404를_반환한다() throws Exception {
        doThrow(new BookNotFoundException(999L)).when(wishlistService).addWishlist(999L, 1L);

        mockMvc.perform(post("/api/wishlist/999").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 찜_삭제는_200을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/wishlist/1").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wishlistService, times(1)).removeWishlist(1L, 1L);
    }
}
```

- [ ] **Step 6: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.WishlistControllerTest"`
Expected: FAIL — `WishlistController` 클래스가 없어 컴파일 실패.

- [ ] **Step 7: WishlistController 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/WishlistController.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{bookId}")
    public ApiResponse<Void> addWishlist(
            @PathVariable Long bookId,
            @RequestHeader("X-Member-Id") Long memberId) {
        wishlistService.addWishlist(bookId, memberId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{bookId}")
    public ApiResponse<Void> removeWishlist(
            @PathVariable Long bookId,
            @RequestHeader("X-Member-Id") Long memberId) {
        wishlistService.removeWishlist(bookId, memberId);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 8: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.WishlistControllerTest"`
Expected: PASS (3 tests)

- [ ] **Step 9: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/service/WishlistService.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/controller/WishlistController.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/service/WishlistServiceTest.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/controller/WishlistControllerTest.java
git commit -m "feat(book): WishlistService/WishlistController 구현"
```

---

### Task 7: RecentViewedBook 엔티티 + RecentViewedBookRepository + 데모 SQL

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/domain/RecentViewedBook.java`
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/repository/RecentViewedBookRepository.java`
- Modify: `db/1_demo_data.sql`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/repository/RecentViewedBookRepositoryTest.java`

**Interfaces:**
- Consumes: `Book`, `Book.builder()`, `BookRepository`, `BookModuleTestApplication` (BOO-6).
- Produces: `RecentViewedBook` — getter: `getId()`, `getMemberId()`, `getBook()`, `getViewedAt()`. `RecentViewedBook.builder().memberId(Long).book(Book).viewedAt(LocalDateTime).build()`. `touch(LocalDateTime now): void`. `RecentViewedBookRepository.findByMemberIdAndBookId(Long, Long): Optional<RecentViewedBook>`, `findByMemberIdOrderByViewedAtDesc(Long, Pageable): List<RecentViewedBook>`. Task 8에서 사용.

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/repository/RecentViewedBookRepositoryTest.java`:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RecentViewedBook;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class RecentViewedBookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private RecentViewedBookRepository recentViewedBookRepository;

    private Book book1;
    private Book book2;

    @BeforeEach
    void setUp() {
        book1 = bookRepository.save(Book.builder()
                .title("최근본책1").author("저자").publisher("출판사").isbn("9791100000051")
                .category("소설").price(10000).stockQuantity(10)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build());
        book2 = bookRepository.save(Book.builder()
                .title("최근본책2").author("저자").publisher("출판사").isbn("9791100000052")
                .category("소설").price(10000).stockQuantity(10)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build());
    }

    @Test
    void 최근_본_기록을_저장하고_조회한다() {
        recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId(1L).book(book1).viewedAt(LocalDateTime.now()).build());

        assertThat(recentViewedBookRepository.findByMemberIdAndBookId(1L, book1.getId())).isPresent();
    }

    @Test
    void 같은_회원_같은_책은_중복_기록될_수_없다() {
        recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId(1L).book(book1).viewedAt(LocalDateTime.now()).build());

        assertThrows(Exception.class, () -> recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId(1L).book(book1).viewedAt(LocalDateTime.now()).build()));
    }

    @Test
    void 최근_본_순으로_조회한다() {
        RecentViewedBook older = recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId(1L).book(book1).viewedAt(LocalDateTime.now().minusDays(1)).build());
        recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId(1L).book(book2).viewedAt(LocalDateTime.now()).build());

        List<RecentViewedBook> result = recentViewedBookRepository
                .findByMemberIdOrderByViewedAtDesc(1L, PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBook().getId()).isEqualTo(book2.getId());
        assertThat(result.get(1).getId()).isEqualTo(older.getId());
    }

    @Test
    void touch로_viewedAt을_갱신한다() {
        RecentViewedBook recentViewedBook = recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId(1L).book(book1).viewedAt(LocalDateTime.now().minusDays(1)).build());
        LocalDateTime newTime = LocalDateTime.now();

        recentViewedBook.touch(newTime);

        assertThat(recentViewedBook.getViewedAt()).isEqualTo(newTime);
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.RecentViewedBookRepositoryTest"`
Expected: FAIL — `RecentViewedBook`, `RecentViewedBookRepository` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: RecentViewedBook 엔티티 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/domain/RecentViewedBook.java`:

```java
package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recent_viewed_books", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentViewedBook extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private LocalDateTime viewedAt;

    @Builder
    public RecentViewedBook(Long memberId, Book book, LocalDateTime viewedAt) {
        this.memberId = memberId;
        this.book = book;
        this.viewedAt = viewedAt;
    }

    public void touch(LocalDateTime now) {
        this.viewedAt = now;
    }
}
```

- [ ] **Step 4: RecentViewedBookRepository 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/repository/RecentViewedBookRepository.java`:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.RecentViewedBook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecentViewedBookRepository extends JpaRepository<RecentViewedBook, Long> {

    Optional<RecentViewedBook> findByMemberIdAndBookId(Long memberId, Long bookId);

    List<RecentViewedBook> findByMemberIdOrderByViewedAtDesc(Long memberId, Pageable pageable);
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.RecentViewedBookRepositoryTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: 데모 SQL에 recent_viewed_books 테이블 추가**

`db/1_demo_data.sql` 파일 끝에 다음을 추가한다:

```sql

CREATE TABLE IF NOT EXISTS recent_viewed_books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    viewed_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recent_viewed_books_member_book (member_id, book_id)
);
```

- [ ] **Step 7: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/domain/RecentViewedBook.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/repository/RecentViewedBookRepository.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/repository/RecentViewedBookRepositoryTest.java \
        db/1_demo_data.sql
git commit -m "feat(book): RecentViewedBook 엔티티 및 RecentViewedBookRepository 추가"
```

---

### Task 8: RecentViewedBookService + BookController 훅

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/service/RecentViewedBookService.java`
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookController.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/service/RecentViewedBookServiceTest.java`
- Modify: `backend/modules/book/src/test/java/com/bookeatinglion/book/controller/BookControllerTest.java`

**Interfaces:**
- Consumes: `RecentViewedBookRepository`, `RecentViewedBook.builder()`, `touch()`(Task 7), `BookRepository`, `Book.builder()`, `BookNotFoundException`, `BookSummaryResponse`(BOO-6).
- Produces: `RecentViewedBookService.recordView(Long bookId, Long memberId): void`, `getMyRecentBooks(Long memberId, int limit): List<BookSummaryResponse>`. Task 9에서 `getMyRecentBooks`를 사용.

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/service/RecentViewedBookServiceTest.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RecentViewedBook;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.RecentViewedBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecentViewedBookServiceTest {

    @Mock
    private RecentViewedBookRepository recentViewedBookRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private RecentViewedBookService recentViewedBookService;

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책").author("저자").publisher("출판사").isbn("978130000" + id)
                .category("소설").price(10000).stockQuantity(5)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        Field idField = Book.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
    }

    @Test
    void 처음_보는_책은_새로_기록한다() throws Exception {
        Book book = book(1L);
        when(recentViewedBookRepository.findByMemberIdAndBookId(1L, 1L)).thenReturn(Optional.empty());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        recentViewedBookService.recordView(1L, 1L);

        verify(recentViewedBookRepository, times(1)).save(any());
    }

    @Test
    void 다시_보는_책은_viewedAt만_갱신하고_새로_저장하지_않는다() throws Exception {
        Book book = book(1L);
        RecentViewedBook existing = RecentViewedBook.builder()
                .memberId(1L).book(book).viewedAt(LocalDateTime.now().minusDays(1)).build();
        when(recentViewedBookRepository.findByMemberIdAndBookId(1L, 1L)).thenReturn(Optional.of(existing));

        recentViewedBookService.recordView(1L, 1L);

        verify(recentViewedBookRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_책_기록은_예외를_던진다() {
        when(recentViewedBookRepository.findByMemberIdAndBookId(1L, 999L)).thenReturn(Optional.empty());
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recentViewedBookService.recordView(999L, 1L))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 내_최근_본_책_목록을_책_요약_정보로_조회한다() throws Exception {
        Book book = book(1L);
        when(recentViewedBookRepository.findByMemberIdOrderByViewedAtDesc(any(), any()))
                .thenReturn(List.of(RecentViewedBook.builder()
                        .memberId(1L).book(book).viewedAt(LocalDateTime.now()).build()));

        List<BookSummaryResponse> result = recentViewedBookService.getMyRecentBooks(1L, 20);

        assertThat(result).extracting(BookSummaryResponse::title).containsExactly("책");
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.RecentViewedBookServiceTest"`
Expected: FAIL — `RecentViewedBookService` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: RecentViewedBookService 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/service/RecentViewedBookService.java`:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RecentViewedBook;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.RecentViewedBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentViewedBookService {

    private final RecentViewedBookRepository recentViewedBookRepository;
    private final BookRepository bookRepository;

    @Transactional
    public void recordView(Long bookId, Long memberId) {
        recentViewedBookRepository.findByMemberIdAndBookId(memberId, bookId).ifPresentOrElse(
                existing -> existing.touch(LocalDateTime.now()), // 영속 상태 엔티티라 dirty checking으로 자동 UPDATE, save() 불필요
                () -> {
                    Book book = bookRepository.findById(bookId)
                            .orElseThrow(() -> new BookNotFoundException(bookId));
                    recentViewedBookRepository.save(RecentViewedBook.builder()
                            .memberId(memberId)
                            .book(book)
                            .viewedAt(LocalDateTime.now())
                            .build());
                });
    }

    public List<BookSummaryResponse> getMyRecentBooks(Long memberId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return recentViewedBookRepository.findByMemberIdOrderByViewedAtDesc(memberId, pageable).stream()
                .map(recentViewedBook -> BookSummaryResponse.from(recentViewedBook.getBook()))
                .toList();
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.RecentViewedBookServiceTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: BookController에 X-Member-Id 훅 추가**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookController.java` 전체를 다음으로 교체:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.service.BookService;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final RecentViewedBookService recentViewedBookService;

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
    public ApiResponse<BookDetailResponse> getBook(
            @PathVariable Long bookId,
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId) {
        if (memberId != null) {
            recentViewedBookService.recordView(bookId, memberId);
        }
        return ApiResponse.success(bookService.getBook(bookId));
    }

    @GetMapping("/{bookId}/synopsis/detail")
    public ApiResponse<BookSynopsisDetailResponse> getSynopsisDetail(@PathVariable Long bookId) {
        return ApiResponse.success(bookService.getSynopsisDetail(bookId));
    }
}
```

- [ ] **Step 6: 기존 BookControllerTest에 RecentViewedBookService mock 추가 + 훅 테스트 추가**

`backend/modules/book/src/test/java/com/bookeatinglion/book/controller/BookControllerTest.java` 전체를 다음으로 교체 (기존 8개 테스트에 mock 필드 및 import만 추가되고, 마지막에 훅 테스트 2개가 추가됨):

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.service.BookService;
import com.bookeatinglion.book.service.RecentViewedBookService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @MockBean
    private RecentViewedBookService recentViewedBookService;

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

    @Test
    void 회원_헤더가_있으면_최근_본_책을_기록한다() throws Exception {
        BookDetailResponse detail = new BookDetailResponse(
                1L, "상세책", "저자", "출판사", "9791100000001", "소설", 10000, 5,
                "cover.jpg", "설명", SaleStatus.ON_SALE, LocalDate.of(2026, 1, 1),
                LocalDateTime.now(), LocalDateTime.now());
        when(bookService.getBook(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/books/1").header("X-Member-Id", "1"))
                .andExpect(status().isOk());

        verify(recentViewedBookService, times(1)).recordView(1L, 1L);
    }

    @Test
    void 회원_헤더가_없으면_최근_본_책을_기록하지_않는다() throws Exception {
        BookDetailResponse detail = new BookDetailResponse(
                1L, "상세책", "저자", "출판사", "9791100000001", "소설", 10000, 5,
                "cover.jpg", "설명", SaleStatus.ON_SALE, LocalDate.of(2026, 1, 1),
                LocalDateTime.now(), LocalDateTime.now());
        when(bookService.getBook(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk());

        verify(recentViewedBookService, never()).recordView(any(), any());
    }
}
```

- [ ] **Step 7: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.BookControllerTest"`
Expected: PASS (10 tests)

- [ ] **Step 8: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/service/RecentViewedBookService.java \
        backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookController.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/service/RecentViewedBookServiceTest.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/controller/BookControllerTest.java
git commit -m "feat(book): RecentViewedBookService 구현 및 BookController에 조회 기록 훅 추가"
```

---

### Task 9: MemberBookQueryController

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/MemberBookQueryController.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/controller/MemberBookQueryControllerTest.java`

**Interfaces:**
- Consumes: `WishlistService.getMyWishlist`(Task 6), `RecentViewedBookService.getMyRecentBooks`(Task 8), `BookSummaryResponse`(BOO-6).
- Produces: `GET /api/members/me/wishlist`, `GET /api/members/me/recent-books`.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/controller/MemberBookQueryControllerTest.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemberBookQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class MemberBookQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WishlistService wishlistService;

    @MockBean
    private RecentViewedBookService recentViewedBookService;

    private BookSummaryResponse summary(Long id, String title) {
        return new BookSummaryResponse(id, title, "저자", 10000, "cover.jpg", "소설", SaleStatus.ON_SALE);
    }

    @Test
    void 내_찜_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(wishlistService.getMyWishlist(1L)).thenReturn(List.of(summary(1L, "찜한책")));

        mockMvc.perform(get("/api/members/me/wishlist").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("찜한책"));
    }

    @Test
    void 내_최근_본_책_조회는_200과_데이터를_반환한다() throws Exception {
        when(recentViewedBookService.getMyRecentBooks(eq(1L), eq(20)))
                .thenReturn(List.of(summary(1L, "최근본책")));

        mockMvc.perform(get("/api/members/me/recent-books").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("최근본책"));
    }

    @Test
    void 최근_본_책_조회시_limit을_지정할_수_있다() throws Exception {
        when(recentViewedBookService.getMyRecentBooks(eq(1L), eq(5)))
                .thenReturn(List.of(summary(1L, "최근본책")));

        mockMvc.perform(get("/api/members/me/recent-books")
                        .header("X-Member-Id", "1")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("최근본책"));
    }

    @Test
    void 회원_헤더가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/members/me/wishlist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.MemberBookQueryControllerTest"`
Expected: FAIL — `MemberBookQueryController` 클래스가 없어 컴파일 실패.

- [ ] **Step 3: MemberBookQueryController 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/MemberBookQueryController.java`:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberBookQueryController {

    private final WishlistService wishlistService;
    private final RecentViewedBookService recentViewedBookService;

    @GetMapping("/wishlist")
    public ApiResponse<List<BookSummaryResponse>> getMyWishlist(
            @RequestHeader("X-Member-Id") Long memberId) {
        return ApiResponse.success(wishlistService.getMyWishlist(memberId));
    }

    @GetMapping("/recent-books")
    public ApiResponse<List<BookSummaryResponse>> getMyRecentBooks(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(recentViewedBookService.getMyRecentBooks(memberId, limit));
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.MemberBookQueryControllerTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: book 모듈 전체 테스트 및 빌드 확인**

Run: `./gradlew :modules:book:test`
Expected: PASS (BOO-6 25개 + BOO-7 신규 테스트 전부)

Run: `./gradlew :modules:book:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/controller/MemberBookQueryController.java \
        backend/modules/book/src/test/java/com/bookeatinglion/book/controller/MemberBookQueryControllerTest.java
git commit -m "feat(book): MemberBookQueryController 구현 (내 찜 목록, 최근 본 상품)"
```

---

## Out of Scope (구현하지 않음)

- 실제 인증/인가(JWT 등) 도입 — `X-Member-Id` 헤더는 임시 수단
- 리뷰 수정(PATCH/PUT), 평점 집계(평균 별점 등 Book 엔티티 반영)
- 최근 본 상품 목록 상한(오래된 행 정리)
- Member 존재 여부 검증 (memberId는 느슨한 참조라 FK 제약 없음)
- `member` 모듈의 기존 컴파일 에러(`MemberService.findByUsername`) — BOO-7과 무관, 별도 이슈로 유지
