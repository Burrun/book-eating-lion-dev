# 마이페이지 "내 이북 보관함" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 마이페이지(`frontend/src/pages/MyPage.jsx`)에 로그인 회원이 실제로 구매 확정하고 + epub 파일을 보유한 책만 보여주는 "내 이북 보관함" 섹션을 추가하고, 클릭 시 Catalog 상세 페이지를 거치지 않고 바로 `EbookViewer`를 여는 흐름을 만든다. 서버 측에서 구매 소유권을 검증해 타인의 bookId로는 열람할 수 없게 한다.

**Architecture:**
- 백엔드: `review_permissions` 테이블(구매 확정 시 order-service가 이벤트로 미리 적재해 둔 스냅샷, 원래는 리뷰 작성 권한용)을 "이 회원이 이 책을 구매 확정했다"는 근거로 재사용한다. 새 컬럼/테이블을 만들지 않는다. `EbookController`의 기존 `GET /api/catalog/books/{bookId}/ebook`에 소유권 검증을 추가하고, `MemberBookQueryController`에 `GET /api/catalog/ebooks/me`(내 이북 보관함 목록)를 새로 추가한다.
- 프론트엔드: `MyPage.jsx`에 목록 조회 + 클릭 시 `EbookViewer`(이미 완성된, 진행률 저장/완독 판정을 자체적으로 처리하는 컴포넌트)를 모달처럼 띄우는 섹션을 추가한다. `EbookViewer` 내부 로직은 손대지 않는다 — `bookId`/`url`/`title`만 넘기면 이어읽기 위치 복원과 진행률 저장은 이미 자동으로 된다.
- Catalog 쪽 기존 진입점(`ProductDetailPage.tsx`)은 그대로 둔다. 같은 백엔드 엔드포인트를 쓰므로 소유권 검증은 두 진입점 모두에 자동으로 적용된다.

**Tech Stack:** Spring Boot (Java 21, Spring Data JPA, Spring Security JWT), React 18 + Vite + TypeScript/JSX 혼용, react-query 없이 useState/useEffect 수동 fetch(마이페이지 기존 컨벤션), openapi-typescript로 생성한 계약 타입.

**Spec:** 이 대화의 작업 지시문(마이페이지에 "내 이북 보관함" 섹션 추가) — 별도 spec 파일 없음. 조사 결과는 아래 "조사 결과 요약"에 정리했다.

## 조사 결과 요약 (구현 전 필수 확인 사항에 대한 답)

1. **`EbookViewer.jsx` 위치/props** — `frontend/src/components/EbookViewer.jsx`. Props: `isOpen, onClose, url, title, bookId, onProgressChange`. 내부에서 `useReadingProgress(bookId)`로 이어읽기 위치를 자체 복원/저장한다. 호출부는 `url`(presigned URL)만 정확히 넘기면 된다 — 새로 만들 것 없음.
2. **Catalog 진입점** — `frontend/src/pages/ProductDetailPage/ProductDetailPage.tsx`의 `handleOpenEbook()` → `getEbookAccess(id)` (POST 아님, GET, `api/books.ts`) → 성공 시 `EbookViewer` 모달을 연다. 별도 라우트가 아니라 상세 페이지 안의 상태 기반 모달이다. 이 구조를 마이페이지에도 그대로 재현한다.
3. **구매/보유 목록 API** — 기존에 없다. 새로 만들어야 하지만 새 테이블은 필요 없다. `review_permissions`(order-service가 구매 확정 시 Redis Streams로 발행 → catalog가 자기 DB에 적재하는 스냅샷, `backend/modules/book/src/main/java/com/bookeatinglion/book/domain/ReviewPermission.java`)가 "이 회원이 이 주문 항목으로 이 책을 구매 확정했다"의 근거 테이블이다. 여기서 `memberId` 기준으로 `bookId` 목록을 뽑고, `Book.isEbookAvailable()`(epub_s3_key 존재)로 필터링하면 "구매 확정 + eBook 보유" 목록이 정확히 나온다.
4. **"SA 문제로 접근 안 됨"의 원인** — 코드/인프라 설정에서 확인됨(추측 아님): `k8s/catalog/deployment.yaml`에 `serviceAccountName`이 없고, `terraform/modules/compute/`에 `catalog_service_iam` 모듈 자체가 없다(반면 `ai_service_iam`, `member_service_iam`은 있고 `k8s/ai/serviceaccount.yaml`, `k8s/member/serviceaccount.yaml`도 있음). 즉 catalog-api 파드는 IRSA 없이 `default` ServiceAccount로 뜬다. `ebooks.storage=s3`로 배포된 환경(dev/prod)에서는 `EbookS3Config`의 `S3Presigner`가 AWS 자격증명을 얻을 방법이 없어 presigned URL 발급이 실패했을 가능성이 매우 높다 — 커밋 `54005c4`(`fix(ai): IRSA ServiceAccount 연결...`)에서 `ai-rag`/`ai-bot`에 대해 이미 겪고 고친 것과 정확히 같은 패턴인데 catalog에는 적용되지 않았다.
   - **이 인프라 수정은 이번 작업 범위 밖이다** (마이페이지 진입점 추가와는 별개 문제이고, 로컬 docker-compose는 `EBOOK_STORAGE=local`이 기본값이라 이 문제의 영향을 받지 않는다 — `LocalEbookStorageAdapter`는 AWS 자격증명이 필요 없다). **다만 이 plan 실행 전 오현님께 이 진단이 맞는지 확인하고, 맞다면 별도 작업으로 `terraform/modules/compute/catalog_service_iam` + `k8s/catalog/serviceaccount.yaml`을 `member_service_iam`/`k8s/member/serviceaccount.yaml`을 본떠 추가해야 한다.**
   - 조사 중 추가로 발견한 것(이건 이번 작업 범위 안): `EbookController.getEbook()`은 현재 **구매 여부를 전혀 검증하지 않는다** — 로그인만 했으면 아무 책이나 열람 URL을 받을 수 있다. 이번 작업의 완료 기준 3번("타인의 bookId 차단")이 바로 이 구멍을 막는 것이다.
5. **epub 보유 도서 2권 확인** — `db/postgres/90-demo-data.sql`에서 `book_id = 101`(프랑켄슈타인), `102`(이상한 나라의 앨리스)만 `epub_s3_key`가 설정되어 있고 나머지는 NULL. `frontend/public/ebooks/`에도 이 두 파일만 존재. `Book.isEbookAvailable()`(`epubS3Key != null`)로 필터링하면 정확히 이 전제와 일치한다.

## Global Constraints

- 새 테이블/컬럼 추가 금지 — `review_permissions`를 소유권 근거로 재사용한다.
- `EbookViewer.jsx`, `useReadingProgress.js`, `COMPLETION_PERCENTAGE_THRESHOLD` 로직 변경 금지 — 그대로 재사용한다.
- 사자 먹이주기(메모 작성 → 벡터 적재) 플로우 변경 금지.
- `api/client.ts`의 `apiClient`/`unwrap()` 패턴, `ProtectedRoute` 인증 흐름을 그대로 따른다.
- Catalog 쪽 기존 `ProductDetailPage.tsx` 진입점은 제거하지 않는다.
- 회원 식별은 항상 JWT `sub`(`CatalogMemberIdentity.requiredMemberId()`) — `X-Member-Id` 헤더 사용 금지(리포 전체 컨벤션).
- 프론트엔드에는 테스트 프레임워크가 설정되어 있지 않다(vitest/jest 없음) — 프론트 검증은 `tsc --noEmit`, `eslint`, `prettier --check`, 로컬 docker-compose e2e로 한다. 백엔드는 JUnit5 + Mockito(`@ExtendWith(MockitoExtension.class)`) / `@DataJpaTest` / `@WebMvcTest` 컨벤션을 따른다.

---

## Task 1: `ReviewPermissionRepository`에 소유권 존재 확인 쿼리 추가

**Files:**
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/repository/ReviewPermissionRepository.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/repository/ReviewPermissionRepositoryTest.java` (신규)

**Interfaces:**
- Produces: `ReviewPermissionRepository.existsByIdMemberIdAndBookId(String memberId, Long bookId): boolean` — Task 3에서 소유권 검증에 쓴다.
- Consumes: 기존 `findByIdMemberId(String memberId): List<ReviewPermission>` (변경 없음, Task 4에서 사용).

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/repository/ReviewPermissionRepositoryTest.java`:

```java
package com.bookeatinglion.book.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.ReviewPermission;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ReviewPermissionRepositoryTest {

    @Autowired
    private ReviewPermissionRepository reviewPermissionRepository;

    @BeforeEach
    void setUp() {
        reviewPermissionRepository.save(
                new ReviewPermission("member-1", 1L, 101L, "닉네임", LocalDateTime.now()));
    }

    @Test
    void 구매_확정한_회원_도서_조합은_존재한다() {
        boolean result = reviewPermissionRepository.existsByIdMemberIdAndBookId("member-1", 101L);

        assertThat(result).isTrue();
    }

    @Test
    void 다른_회원의_구매_확정_내역은_존재하지_않는다() {
        boolean result = reviewPermissionRepository.existsByIdMemberIdAndBookId("member-2", 101L);

        assertThat(result).isFalse();
    }

    @Test
    void 구매하지_않은_책은_존재하지_않는다() {
        boolean result = reviewPermissionRepository.existsByIdMemberIdAndBookId("member-1", 999L);

        assertThat(result).isFalse();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.ReviewPermissionRepositoryTest"`
Expected: FAIL — `existsByIdMemberIdAndBookId` method not found (컴파일 에러).

- [ ] **Step 3: 최소 구현**

`backend/modules/book/src/main/java/com/bookeatinglion/book/repository/ReviewPermissionRepository.java` 전체를 아래로 교체:

```java
package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.domain.ReviewPermissionId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewPermissionRepository extends JpaRepository<ReviewPermission, ReviewPermissionId> {

    /**
     * 아직 쓰지 않은 권한 1건. 리뷰 작성은 이 로컬 조회 하나로 끝난다 — 네트워크 홉 0.
     */
    Optional<ReviewPermission> findFirstByIdMemberIdAndBookIdAndUsedAtIsNull(String memberId, Long bookId);

    List<ReviewPermission> findByIdMemberId(String memberId);

    /** eBook 열람 시 "이 회원이 이 책을 구매 확정했는가"를 확인하는 용도. usedAt 여부는 무관하다(리뷰 소진과 무관). */
    boolean existsByIdMemberIdAndBookId(String memberId, Long bookId);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :modules:book:test --tests "com.bookeatinglion.book.repository.ReviewPermissionRepositoryTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/repository/ReviewPermissionRepository.java backend/modules/book/src/test/java/com/bookeatinglion/book/repository/ReviewPermissionRepositoryTest.java
git commit -m "feat: ReviewPermissionRepository에 회원×도서 구매 확정 존재 확인 쿼리 추가"
```

---

## Task 2: eBook 소유권 미보유 예외/에러코드 추가

**Files:**
- Create: `backend/modules/book/src/main/java/com/bookeatinglion/book/exception/EbookOwnershipRequiredException.java`
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/exception/BookErrorCode.java`
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java`

**Interfaces:**
- Produces: `EbookOwnershipRequiredException(Long bookId)` — Task 3의 `EbookService.getAccess`가 던진다. `BookErrorCode.EBOOK_OWNERSHIP_REQUIRED`(403)로 매핑된다.

이 태스크는 컨트롤러/서비스 테스트 없이도 독립적으로 컴파일되므로 별도 단위 테스트 없이 진행하고, Task 3의 컨트롤러 테스트에서 실제 403 응답을 검증한다.

- [ ] **Step 1: 예외 클래스 작성**

`backend/modules/book/src/main/java/com/bookeatinglion/book/exception/EbookOwnershipRequiredException.java`:

```java
package com.bookeatinglion.book.exception;

/** 구매 확정(review_permissions) 기록이 없는 회원이 eBook 열람 URL을 요청할 때 던진다. */
public class EbookOwnershipRequiredException extends RuntimeException {

    public EbookOwnershipRequiredException(Long bookId) {
        super("구매 확정 내역이 없어 eBook을 열람할 수 없습니다: bookId=" + bookId);
    }
}
```

- [ ] **Step 2: 에러코드 추가**

`backend/modules/book/src/main/java/com/bookeatinglion/book/exception/BookErrorCode.java`에서 `EBOOK_ACCESS_UNAVAILABLE`과 `INVALID_REQUEST` 사이에 추가:

```java
    EBOOK_ACCESS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    EBOOK_OWNERSHIP_REQUIRED(HttpStatus.FORBIDDEN),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);
```

- [ ] **Step 3: 핸들러 추가**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java`에서 import 목록에 추가(알파벳 순, `EbookAccessUnavailableException` 바로 뒤):

```java
import com.bookeatinglion.book.exception.EbookAccessUnavailableException;
import com.bookeatinglion.book.exception.EbookOwnershipRequiredException;
```

그리고 `handleEbookAccessUnavailable` 메서드 바로 뒤에 추가:

```java
    @ExceptionHandler(EbookOwnershipRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleEbookOwnershipRequired(EbookOwnershipRequiredException e) {
        return ResponseEntity.status(BookErrorCode.EBOOK_OWNERSHIP_REQUIRED.getStatus())
                .body(ApiResponse.error(BookErrorCode.EBOOK_OWNERSHIP_REQUIRED.name(), e.getMessage()));
    }
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew :modules:book:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/exception/EbookOwnershipRequiredException.java backend/modules/book/src/main/java/com/bookeatinglion/book/exception/BookErrorCode.java backend/modules/book/src/main/java/com/bookeatinglion/book/controller/BookExceptionHandler.java
git commit -m "feat: eBook 소유권 미보유(403) 예외/에러코드 추가"
```

---

## Task 3: `EbookService.getAccess`에 소유권 검증 적용 + 컨트롤러 연결

**Files:**
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/service/EbookService.java`
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/EbookController.java`
- Modify: `backend/modules/book/src/test/java/com/bookeatinglion/book/service/EbookServiceTest.java`
- Test: `backend/modules/book/src/test/java/com/bookeatinglion/book/controller/EbookControllerTest.java` (신규)

**Interfaces:**
- Consumes: `ReviewPermissionRepository.existsByIdMemberIdAndBookId` (Task 1), `EbookOwnershipRequiredException` (Task 2), `CatalogMemberIdentity.requiredMemberId(): String` (기존).
- Produces: `EbookService.getAccess(Long bookId, String memberId): EbookAccessResponse` — 시그니처 변경(기존 `getAccess(Long bookId)`에서 `memberId` 파라미터 추가). Task 4의 `getMyEbooks`도 같은 클래스에 추가되므로 이 서비스가 `ReviewPermissionRepository`를 갖게 된다.

- [ ] **Step 1: 실패하는 테스트로 `EbookServiceTest` 갱신**

`backend/modules/book/src/test/java/com/bookeatinglion/book/service/EbookServiceTest.java` 전체를 아래로 교체:

```java
package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.exception.EbookOwnershipRequiredException;
import com.bookeatinglion.book.port.EbookStoragePort;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewPermissionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EbookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ReviewPermissionRepository reviewPermissionRepository;

    @Mock
    private EbookStoragePort ebookStoragePort;

    private EbookService ebookService;

    @BeforeEach
    void setUp() {
        ebookService = new EbookService(
                bookRepository,
                reviewPermissionRepository,
                ebookStoragePort,
                Duration.ofMinutes(10),
                Duration.ofMinutes(10));
    }

    @Test
    void ebook이_없는_도서는_구매_여부와_무관하게_미지원으로_응답한다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book(null)));

        EbookAccessResponse result = ebookService.getAccess(1L, "member-1");

        assertThat(result.ebookAvailable()).isFalse();
        assertThat(result.presignedUrl()).isNull();
        verifyNoInteractions(ebookStoragePort);
        verifyNoInteractions(reviewPermissionRepository);
    }

    @Test
    void 구매_확정한_회원에게는_열람_URL을_발급한다() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book("ebooks/alice.epub")));
        when(reviewPermissionRepository.existsByIdMemberIdAndBookId("member-1", 1L)).thenReturn(true);
        when(ebookStoragePort.createReadUrl("ebooks/alice.epub", Duration.ofMinutes(10)))
                .thenReturn(new EbookStoragePort.ReadUrl("https://signed.example/alice", expiresAt));

        EbookAccessResponse result = ebookService.getAccess(1L, "member-1");

        assertThat(result.ebookAvailable()).isTrue();
        assertThat(result.presignedUrl()).isEqualTo("https://signed.example/alice");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void 구매하지_않은_회원은_403에_해당하는_예외를_받는다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book("ebooks/alice.epub")));
        when(reviewPermissionRepository.existsByIdMemberIdAndBookId("member-2", 1L)).thenReturn(false);

        assertThatThrownBy(() -> ebookService.getAccess(1L, "member-2"))
                .isInstanceOf(EbookOwnershipRequiredException.class);
        verifyNoInteractions(ebookStoragePort);
    }

    @Test
    void 신간_등록_화면에서_업로드_URL을_발급한다() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);
        when(ebookStoragePort.createUploadUrl("alice.epub", Duration.ofMinutes(10)))
                .thenReturn(new EbookStoragePort.UploadUrl(
                        "https://signed.example/upload", "epubs/uuid_alice.epub", expiresAt));

        var result = ebookService.issueUploadUrl("alice.epub");

        assertThat(result.uploadUrl()).isEqualTo("https://signed.example/upload");
        assertThat(result.epubS3Key()).isEqualTo("epubs/uuid_alice.epub");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    private Book book(String epubS3Key) {
        return Book.builder()
                .title("앨리스")
                .author("루이스 캐럴")
                .publisher("공개 도서")
                .isbn("9791100000001")
                .category("소설")
                .price(0)
                .epubS3Key(epubS3Key)
                .saleStatus(SaleStatus.ON_SALE)
                .salesCount(0)
                .build();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd backend && ./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.EbookServiceTest"`
Expected: FAIL — 컴파일 에러(생성자 인자 개수 불일치, `getAccess(Long, String)` 없음).

- [ ] **Step 3: `EbookService` 구현**

`backend/modules/book/src/main/java/com/bookeatinglion/book/service/EbookService.java` 전체를 아래로 교체:

```java
package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.dto.EpubUploadUrlResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.EbookOwnershipRequiredException;
import com.bookeatinglion.book.port.EbookStoragePort;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewPermissionRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EbookService {

    private final BookRepository bookRepository;
    private final ReviewPermissionRepository reviewPermissionRepository;
    private final EbookStoragePort ebookStoragePort;
    private final Duration readUrlValidity;
    private final Duration uploadUrlValidity;

    public EbookService(
            BookRepository bookRepository,
            ReviewPermissionRepository reviewPermissionRepository,
            EbookStoragePort ebookStoragePort,
            @Value("${ebooks.read-url-validity:PT10M}") Duration readUrlValidity,
            @Value("${ebooks.upload-url-validity:PT10M}") Duration uploadUrlValidity) {
        this.bookRepository = bookRepository;
        this.reviewPermissionRepository = reviewPermissionRepository;
        this.ebookStoragePort = ebookStoragePort;
        this.readUrlValidity = readUrlValidity;
        this.uploadUrlValidity = uploadUrlValidity;
    }

    /**
     * eBook 미지원 도서는 구매 여부와 무관하게 미지원으로 응답한다. 지원 도서는 review_permissions에
     * 구매 확정 기록이 있는 회원에게만 presigned URL을 발급한다 — 없으면 403(EbookOwnershipRequiredException).
     */
    public EbookAccessResponse getAccess(Long bookId, String memberId) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        if (!book.isEbookAvailable()) {
            return EbookAccessResponse.unavailable(bookId);
        }
        if (!reviewPermissionRepository.existsByIdMemberIdAndBookId(memberId, bookId)) {
            throw new EbookOwnershipRequiredException(bookId);
        }
        EbookStoragePort.ReadUrl readUrl = ebookStoragePort.createReadUrl(book.getEpubS3Key(), readUrlValidity);
        return new EbookAccessResponse(bookId, true, readUrl.url(), readUrl.expiresAt());
    }

    /** 신간 등록 화면에서 EPUB 파일을 고르면 바로 호출한다 — 도서가 아직 없어도 된다. */
    public EpubUploadUrlResponse issueUploadUrl(String fileName) {
        EbookStoragePort.UploadUrl uploadUrl = ebookStoragePort.createUploadUrl(fileName, uploadUrlValidity);
        return new EpubUploadUrlResponse(uploadUrl.url(), uploadUrl.key(), uploadUrl.expiresAt());
    }

    /** 내 이북 보관함: 구매 확정(review_permissions) + eBook 보유(epub_s3_key) 도서만 반환한다. */
    public List<BookSummaryResponse> getMyEbooks(String memberId) {
        List<Long> purchasedBookIds = reviewPermissionRepository.findByIdMemberId(memberId).stream()
                .map(ReviewPermission::getBookId)
                .distinct()
                .toList();
        if (purchasedBookIds.isEmpty()) {
            return List.of();
        }
        return bookRepository.findByBookIdInAndEpubS3KeyIsNotNullAndIsDeletedFalse(purchasedBookIds).stream()
                .map(BookSummaryResponse::from)
                .toList();
    }
}
```

(주: `getMyEbooks`와 `BookRepository.findByBookIdInAndEpubS3KeyIsNotNullAndIsDeletedFalse`는 Task 4에서 마저 배선한다. 지금은 `EbookService`에 메서드만 추가해 컴파일이 되게 한다 — Task 4에서 리포지토리 메서드를 추가하지 않으면 이 시점엔 컴파일 에러가 난다. 순서를 지켜라: 이 Step 3 코드는 Task 4 Step 1(리포지토리 메서드 추가)까지 끝나야 컴파일된다. Step 4에서 함께 확인한다.)

- [ ] **Step 3-b: `BookRepository`에 조회 메서드 선추가 (Task 4와 중복 방지용 — 여기서 한 번만 추가)**

`backend/modules/book/src/main/java/com/bookeatinglion/book/repository/BookRepository.java`에서 `findByEpubS3KeyIsNotNullAndIsDeletedFalse` 메서드 바로 아래에 추가:

```java
    Page<Book> findByEpubS3KeyIsNotNullAndIsDeletedFalse(Pageable pageable);

    List<Book> findByBookIdInAndEpubS3KeyIsNotNullAndIsDeletedFalse(List<Long> bookIds);

```

- [ ] **Step 4: 컨트롤러 갱신**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/EbookController.java` 전체를 아래로 교체:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.EbookService;
import com.bookeatinglion.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EbookController {

    private final EbookService ebookService;
    private final CatalogMemberIdentity memberIdentity;

    @GetMapping("/api/catalog/books/{bookId}/ebook")
    public ApiResponse<EbookAccessResponse> getEbook(@PathVariable Long bookId) {
        return ApiResponse.success(ebookService.getAccess(bookId, memberIdentity.requiredMemberId()));
    }
}
```

- [ ] **Step 5: `EbookServiceTest` 통과 확인**

Run: `cd backend && ./gradlew :modules:book:test --tests "com.bookeatinglion.book.service.EbookServiceTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: 컨트롤러 403 응답을 검증하는 신규 테스트 작성**

`backend/modules/book/src/test/java/com/bookeatinglion/book/controller/EbookControllerTest.java` (신규):

```java
package com.bookeatinglion.book.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.EbookOwnershipRequiredException;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.EbookService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {EbookController.class, BookExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class EbookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EbookService ebookService;

    @MockBean
    private CatalogMemberIdentity memberIdentity;

    @Test
    void 구매한_회원은_200과_열람_URL을_받는다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(ebookService.getAccess(101L, "member-1"))
                .thenReturn(new EbookAccessResponse(101L, true, "https://signed.example/frankenstein", OffsetDateTime.now()));

        mockMvc.perform(get("/api/catalog/books/101/ebook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl").value("https://signed.example/frankenstein"));
    }

    @Test
    void 구매하지_않은_회원은_403을_받는다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-2");
        when(ebookService.getAccess(101L, "member-2")).thenThrow(new EbookOwnershipRequiredException(101L));

        mockMvc.perform(get("/api/catalog/books/101/ebook"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EBOOK_OWNERSHIP_REQUIRED"));
    }

    @Test
    void 존재하지_않는_도서는_404를_받는다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(ebookService.getAccess(999L, "member-1")).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/catalog/books/999/ebook")).andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd backend && ./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.EbookControllerTest"`
Expected: PASS (3 tests)

- [ ] **Step 8: 전체 book 모듈 테스트로 회귀 확인**

Run: `cd backend && ./gradlew :modules:book:test`
Expected: BUILD SUCCESSFUL (기존 `EbookServiceTest` 호출부가 남아있던 다른 테스트가 없는지 확인 — `SecurityConfigTest`는 `EbookService`를 `@MockBean`으로 쓰므로 시그니처 변경의 영향을 받지 않는다)

- [ ] **Step 9: 커밋**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/service/EbookService.java backend/modules/book/src/main/java/com/bookeatinglion/book/controller/EbookController.java backend/modules/book/src/main/java/com/bookeatinglion/book/repository/BookRepository.java backend/modules/book/src/test/java/com/bookeatinglion/book/service/EbookServiceTest.java backend/modules/book/src/test/java/com/bookeatinglion/book/controller/EbookControllerTest.java
git commit -m "feat: eBook 열람 API에 구매 확정 소유권 검증 추가 (403 EBOOK_OWNERSHIP_REQUIRED)"
```

---

## Task 4: `GET /api/catalog/ebooks/me` — 내 이북 보관함 목록 API

**Files:**
- Modify: `backend/modules/book/src/main/java/com/bookeatinglion/book/controller/MemberBookQueryController.java`
- Modify: `backend/apps/catalog-api/src/main/java/com/bookeatinglion/catalog/api/config/SecurityConfig.java`
- Modify: `backend/apps/catalog-api/src/test/java/com/bookeatinglion/catalog/api/config/SecurityConfigTest.java`
- Modify: `backend/modules/book/src/test/java/com/bookeatinglion/book/controller/MemberBookQueryControllerTest.java`

**Interfaces:**
- Consumes: `EbookService.getMyEbooks(String memberId): List<BookSummaryResponse>` (Task 3에서 이미 구현·컴파일됨).
- Produces: `GET /api/catalog/ebooks/me` → `ApiResponse<List<BookSummaryResponse>>`. 프론트 `api/books.ts`의 `getMyEbooks()`(Task 6)가 호출한다.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 추가**

`backend/modules/book/src/test/java/com/bookeatinglion/book/controller/MemberBookQueryControllerTest.java` 전체를 아래로 교체:

```java
package com.bookeatinglion.book.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.EbookService;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

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

    @MockBean
    private EbookService ebookService;

    @MockBean
    private CatalogMemberIdentity memberIdentity;

    private BookSummaryResponse summary(Long id, String title) {
        return new BookSummaryResponse(
                id, title, "저자", 10000, "cover.jpg", "소설", SaleStatus.ON_SALE, BigDecimal.ZERO, 0, true);
    }

    @Test
    void 내_찜_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(wishlistService.getMyWishlist("member-1")).thenReturn(List.of(summary(1L, "찜한책")));

        mockMvc.perform(get("/api/catalog/wishlist/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("찜한책"));
    }

    @Test
    void 내_최근_본_책_조회는_200과_데이터를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(recentViewedBookService.getMyRecentBooks(eq("member-1"), eq(20))).thenReturn(List.of(summary(1L, "최근본책")));

        mockMvc.perform(get("/api/catalog/recent-books/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("최근본책"));
    }

    @Test
    void 최근_본_책_조회시_limit을_지정할_수_있다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(recentViewedBookService.getMyRecentBooks(eq("member-1"), eq(5))).thenReturn(List.of(summary(1L, "최근본책")));

        mockMvc.perform(get("/api/catalog/recent-books/me").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("최근본책"));
    }

    @Test
    void 내_이북_보관함_조회는_200과_데이터를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(ebookService.getMyEbooks("member-1")).thenReturn(List.of(summary(101L, "프랑켄슈타인")));

        mockMvc.perform(get("/api/catalog/ebooks/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("프랑켄슈타인"))
                .andExpect(jsonPath("$.data[0].ebookAvailable").value(true));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.MemberBookQueryControllerTest"`
Expected: FAIL — `/api/catalog/ebooks/me` 404, `EbookService` 빈 없음(컴파일은 되지만 매핑 자체가 없어 테스트 실패).

- [ ] **Step 3: 컨트롤러 구현**

`backend/modules/book/src/main/java/com/bookeatinglion/book/controller/MemberBookQueryController.java` 전체를 아래로 교체:

```java
package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.EbookService;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로가 /api/members/me/* 에서 /api/catalog/wishlist/me · /api/catalog/recent-books/me ·
 * /api/catalog/ebooks/me 로 바뀌었다.
 *
 * 이유는 Ingress 경로 라우팅이다(§7.5). /api/members/** 는 member-service 로 가는데,
 * 찜 목록·최근 본 상품·내 이북 보관함은 전부 catalog_db 소유 데이터라 catalog-service 가
 * 응답해야 한다. 같은 접두사를 두 서비스가 나눠 가지면 라우팅 규칙이 경로 길이에 의존하게
 * 되고, 규칙 하나만 잘못 건드려도 요청이 엉뚱한 서비스로 간다.
 *
 * 프론트엔드 frontend/src/api/wishlist.ts, frontend/src/api/books.ts 도 함께 수정했다.
 */
@RestController
@RequiredArgsConstructor
public class MemberBookQueryController {

    private final WishlistService wishlistService;
    private final RecentViewedBookService recentViewedBookService;
    private final EbookService ebookService;
    private final CatalogMemberIdentity memberIdentity;

    @GetMapping("/api/catalog/wishlist/me")
    public ApiResponse<List<BookSummaryResponse>> getMyWishlist() {
        return ApiResponse.success(wishlistService.getMyWishlist(memberIdentity.requiredMemberId()));
    }

    @GetMapping("/api/catalog/recent-books/me")
    public ApiResponse<List<BookSummaryResponse>> getMyRecentBooks(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(recentViewedBookService.getMyRecentBooks(memberIdentity.requiredMemberId(), limit));
    }

    /** 구매 확정 + eBook 보유 도서만. 마이페이지 "내 이북 보관함" 섹션이 쓴다. */
    @GetMapping("/api/catalog/ebooks/me")
    public ApiResponse<List<BookSummaryResponse>> getMyEbooks() {
        return ApiResponse.success(ebookService.getMyEbooks(memberIdentity.requiredMemberId()));
    }
}
```

- [ ] **Step 4: `SecurityConfig`에 인증 규칙 추가**

`backend/apps/catalog-api/src/main/java/com/bookeatinglion/catalog/api/config/SecurityConfig.java`에서 `.requestMatchers("/api/catalog/wishlist/**", "/api/catalog/recent-books/**").authenticated()` 줄 바로 뒤에 추가:

```java
                        .requestMatchers("/api/catalog/wishlist/**", "/api/catalog/recent-books/**")
                        .authenticated()
                        .requestMatchers("/api/catalog/ebooks/me")
                        .authenticated()
```

- [ ] **Step 5: `SecurityConfigTest`에 401 케이스 추가**

`backend/apps/catalog-api/src/test/java/com/bookeatinglion/catalog/api/config/SecurityConfigTest.java`에서 `@WebMvcTest` 대상 컨트롤러 목록에 `MemberBookQueryController.class`를 추가하고, `EbookService`/`CatalogMemberIdentity` 외에 `WishlistService`·`RecentViewedBookService`도 목으로 추가해야 컨텍스트가 로드된다. 파일 전체를 아래로 교체:

```java
package com.bookeatinglion.catalog.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.controller.BookExceptionHandler;
import com.bookeatinglion.book.controller.EbookController;
import com.bookeatinglion.book.controller.MemberBookQueryController;
import com.bookeatinglion.book.controller.ReadingProgressController;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.EbookService;
import com.bookeatinglion.book.service.ReadingProgressService;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.catalog.api.test.CatalogApiModuleTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig의 실제 authorizeHttpRequests 규칙(순서 포함)을 검증한다.
 *
 * 회귀 배경: "/api/catalog/books/*_/reading-progress"를 authenticated()로 추가했을 때, 먼저 선언된
 * "GET /api/catalog/books/**".permitAll() 규칙이 먼저 매칭돼 GET이 인증 없이 통과하는 버그가 있었다
 * (첫 매칭 규칙이 이기는 authorizeHttpRequests의 특성). 규칙 순서를 permitAll보다 앞으로
 * 옮겨서 고쳤는데, book 모듈의 ReadingProgressControllerTest는 이 SecurityConfig 빈을 아예
 * 로드하지 않아 이 순서 문제를 검증하지 못한다 — jwt() 로 인증된 요청만 확인했을 뿐,
 * "인증 없이" 케이스는 Spring Security의 일반 기본값(전부 인증 필요)에 기대고 있었다.
 * 그 기본값은 permitAll 규칙이 있든 없든 항상 인증을 요구하므로, 이 순서 버그를 절대
 * 잡아내지 못한다. 이 테스트는 실제 SecurityConfig 빈을 @Import 해서 그 특정 규칙 순서를
 * 직접 검증한다.
 */
@WebMvcTest(
        controllers = {
            ReadingProgressController.class,
            EbookController.class,
            MemberBookQueryController.class,
            BookExceptionHandler.class
        })
@Import({SecurityConfig.class, CatalogMemberIdentity.class})
@ContextConfiguration(classes = CatalogApiModuleTestApplication.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingProgressService readingProgressService;

    @MockBean
    private EbookService ebookService;

    @MockBean
    private WishlistService wishlistService;

    @MockBean
    private RecentViewedBookService recentViewedBookService;

    @Test
    void 인증_없이_이어읽기_위치_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/catalog/books/1/reading-progress")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_이어읽기_위치_저장은_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/catalog/books/1/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cfi\":\"epubcfi(/6/4)\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_ebook_URL_발급은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/catalog/books/1/ebook")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_내_이북_보관함_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/catalog/ebooks/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_내_리뷰_목록_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/catalog/reviews/me")).andExpect(status().isUnauthorized());
    }
}
```

(주: 기존 "인증_없이_내_리뷰_목록_조회는_401을_반환한다" 테스트는 `/api/catalog/reviews/me`를 치는데 이 컨트롤러는 `@WebMvcTest` 대상에 없다 — 원래 파일도 그랬다. Spring Security 기본값(모든 요청 인증 필요)에 기대는 케이스이므로 그대로 둔다.)

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew :modules:book:test --tests "com.bookeatinglion.book.controller.MemberBookQueryControllerTest"`
Run: `cd backend && ./gradlew :apps:catalog-api:test --tests "com.bookeatinglion.catalog.api.config.SecurityConfigTest"`
Expected: 둘 다 PASS

- [ ] **Step 7: 백엔드 전체 테스트로 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋**

```bash
git add backend/modules/book/src/main/java/com/bookeatinglion/book/controller/MemberBookQueryController.java backend/apps/catalog-api/src/main/java/com/bookeatinglion/catalog/api/config/SecurityConfig.java backend/apps/catalog-api/src/test/java/com/bookeatinglion/catalog/api/config/SecurityConfigTest.java backend/modules/book/src/test/java/com/bookeatinglion/book/controller/MemberBookQueryControllerTest.java
git commit -m "feat: GET /api/catalog/ebooks/me — 내 이북 보관함 목록 API 추가"
```

---

## Task 5: OpenAPI 계약 갱신 + 프론트 생성 타입 재생성

**Files:**
- Modify: `backend/contracts/catalog-v1.yaml`
- Modify: `frontend/src/api/generated/catalog.ts` (재생성 산출물)

**Interfaces:**
- Consumes: 없음(문서/타입 전용 변경, 런타임 동작 없음).
- Produces: `backend/contracts/catalog-v1.yaml`의 `/api/catalog/ebooks/me` 경로 정의 — 프론트 코드가 직접 참조하진 않지만("계약이 곧 타입") 팀 컨벤션상 API 추가 시 계약도 함께 갱신한다.

- [ ] **Step 1: `/api/catalog/books/{bookId}/ebook`에 403 응답 문서 추가**

`backend/contracts/catalog-v1.yaml`의 `/api/catalog/books/{bookId}/ebook` 항목에서 `"404"` 줄 앞에 추가:

```yaml
        "403": { description: 구매하지 않은 도서 (EBOOK_OWNERSHIP_REQUIRED) }
        "404": { description: 존재하지 않거나 삭제된 도서 (BOOK_NOT_FOUND) }
```

- [ ] **Step 2: `/api/catalog/ebooks/me` 경로 추가**

같은 파일의 `/api/catalog/recent-books/me` 항목 바로 뒤(다음 경로 시작 전)에 추가:

```yaml
  /api/catalog/ebooks/me:
    get:
      summary: 내 이북 보관함
      description: |
        review_permissions(구매 확정 스냅샷)에 있는 도서 중 eBook(epub_s3_key)이 있는 것만 반환한다.
        마이페이지에서 이 목록을 눌러 바로 리더를 연다.
      operationId: getMyEbooks
      tags: [ebooks]
      security:
        - bearerAuth: []
      responses:
        "200":
          description: 조회 성공
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/BookSummaryListEnvelope"
```

- [ ] **Step 3: 프론트 생성 타입 재생성**

Run (frontend 디렉터리에서): `pnpm dlx openapi-typescript backend/contracts/catalog-v1.yaml -o src/api/generated/catalog.ts`

(주: 이미 이 프로젝트가 쓰는 실제 생성 커맨드/대상 파일이다 — `src/api/types.ts`의 파사드 주석에 "이 파일을 openapi-typescript 의 -o 대상으로 지정하지 말 것"이라고 명시되어 있고, `types.ts`는 `./generated/catalog.ts`를 import한다. README의 `-o src/api/types.ts` 예시는 대상 파일 표기가 오래되어 실제 파사드 구조와 다르니 따르지 말 것.)

이 프로젝트에는 새 스키마 타입을 추가하지 않았으므로(`BookSummaryListEnvelope`/`BookSummary` 재사용) 재생성 결과가 기존 파일과 실질적으로 같을 수 있다 — `git diff frontend/src/api/generated/catalog.ts`로 확인하고, 새 `/api/catalog/ebooks/me` 오퍼레이션 타입이 추가됐는지만 확인한다. openapi-typescript 실행 환경(pnpm/네트워크)이 없으면 이 스텝은 스킵하고 PR 설명에 "계약 갱신함, 생성 타입 재생성은 로컬에서 필요" 라고 남긴다 — Task 6의 프론트 코드는 `types.ts`의 기존 `BookSummaryResponse`/`ApiResponse` 타입만 쓰므로 재생성 여부와 무관하게 컴파일된다.

- [ ] **Step 4: 커밋**

```bash
git add backend/contracts/catalog-v1.yaml frontend/src/api/generated/catalog.ts
git commit -m "docs: /api/catalog/ebooks/me 계약 추가, eBook 열람 403 응답 문서화"
```

---

## Task 6: 프론트 API 함수 — `getMyEbooks()`

**Files:**
- Modify: `frontend/src/api/books.ts`
- Modify: `frontend/src/mocks/books.ts`

**Interfaces:**
- Produces: `getMyEbooks(): Promise<BookSummary[]>` — Task 7의 `MyPage.jsx`가 import해서 쓴다. `BookSummary`는 `frontend/src/types/book.ts`의 기존 타입(필드: `id: string, title, price, rating, category, coverImageUrl, ebookAvailable`).
- Consumes: `apiClient`/`unwrap` (`api/client.ts`), `toBookSummary` (`api/mappers.ts`) — 전부 기존 것, 변경 없음.

- [ ] **Step 1: 목업 함수 추가**

`frontend/src/mocks/books.ts`에서 `mockGetEbookAccess` 함수 바로 뒤에 추가:

```ts
export function mockGetMyEbooks(): BookSummaryResponse[] {
  return SEEDS.filter((seed) => Boolean(seed.ebookUrl)).map(toSummary);
}
```

- [ ] **Step 2: `api/books.ts`에 함수 추가**

`frontend/src/api/books.ts`의 import 블록을 아래로 교체(목업 import에 `mockGetMyEbooks` 추가):

```ts
import {
  mockGetBestsellers,
  mockGetBook,
  mockGetEbookAccess,
  mockGetMyEbooks,
  mockGetBooks,
  mockGetNewReleases,
  mockGetSynopsisDetail,
  mockSearchBooks,
} from "../mocks/books.ts";
```

그리고 `getEbookAccess` 함수 바로 뒤(파일의 해당 함수 끝, 다음 함수 시작 전)에 추가:

```ts
// GET /api/catalog/ebooks/me — 내 이북 보관함(구매 확정 + eBook 보유 도서만, JWT 인증 필요)
export async function getMyEbooks(): Promise<BookSummary[]> {
  const list = USE_MOCK
    ? await mockDelay(mockGetMyEbooks())
    : await unwrap(apiClient.get<ApiResponse<BookSummaryResponse[]>>("/catalog/ebooks/me"));
  return list.map(toBookSummary);
}
```

- [ ] **Step 3: 타입 체크로 확인**

Run: `cd frontend && npx tsc --noEmit`
Expected: 에러 없음 (기존 에러가 있었다면 그 개수에서 늘지 않아야 한다 — `git stash`로 비교 가능)

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/api/books.ts frontend/src/mocks/books.ts
git commit -m "feat: getMyEbooks() API 함수 추가 (내 이북 보관함)"
```

---

## Task 7: `MyPage.jsx`에 "내 이북 보관함" 섹션 추가

**Files:**
- Modify: `frontend/src/pages/MyPage.jsx`

**Interfaces:**
- Consumes: `getMyEbooks()`, `getEbookAccess()` (`api/books.ts`), `EbookViewer` (`components/EbookViewer.jsx`), `useToast()` (`components/Toast.jsx`), `EmptyState`/`TabSkeleton`/`BookActivityTitle`(이 파일 안에 이미 정의된 헬퍼) — 전부 기존 것.
- Produces: `MyEbookShelfSection()` — `MyPage()`의 return 안에서 렌더링.

- [ ] **Step 1: import 추가**

`frontend/src/pages/MyPage.jsx` 최상단 import 블록에서 `import { getFeedableMemos, getFedMemos, markMemoFed } from "../api/bookMemo.ts";` 줄 바로 뒤에 추가:

```jsx
import { getEbookAccess, getMyEbooks } from "../api/books.ts";
import EbookViewer from "../components/EbookViewer.jsx";
```

- [ ] **Step 2: `MyPage()`의 return에 섹션 추가**

같은 파일에서 아래 블록(현재 `MyPage` 함수의 return 부분)을:

```jsx
      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <LionRagCard />
        <OrdersSection />
      </div>

      <BookActivitySection />

      <ReviewsSection />
```

아래로 교체:

```jsx
      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <LionRagCard />
        <OrdersSection />
      </div>

      <MyEbookShelfSection />

      <BookActivitySection />

      <ReviewsSection />
```

- [ ] **Step 3: `MyEbookShelfSection` 컴포넌트 추가**

같은 파일에서 `BookActivitySection` 함수 정의 바로 앞(`function BookActivitySection() {` 줄 바로 위)에 아래 컴포넌트 전체를 추가:

```jsx
/**
 * 구매 확정 + eBook 보유 도서만 GET /api/catalog/ebooks/me 로 받아 목록을 보여준다.
 * 클릭하면 Catalog 상세 페이지를 거치지 않고 바로 EbookViewer를 연다 — 소유권 검증은
 * getEbookAccess가 호출하는 서버 쪽(EbookService.getAccess)에서 한다. EbookViewer 내부의
 * 이어읽기 위치 복원/저장(useReadingProgress)과 완독 판정 로직은 그대로 재사용한다 —
 * bookId/url만 넘기면 된다.
 */
function MyEbookShelfSection() {
  const toast = useToast();
  const [ebooks, setEbooks] = useState(null);
  const [loadError, setLoadError] = useState(false);
  const [openingBookId, setOpeningBookId] = useState(null);
  const [activeBook, setActiveBook] = useState(null); // { id, title } | null
  const [ebookUrl, setEbookUrl] = useState(null);

  useEffect(() => {
    let ignore = false;
    getMyEbooks()
      .then((data) => {
        if (!ignore) setEbooks(data);
      })
      .catch(() => {
        if (!ignore) {
          setEbooks([]);
          setLoadError(true);
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  const handleOpen = async (book) => {
    setOpeningBookId(book.id);
    try {
      const access = await getEbookAccess(book.id);
      if (!access.ebookAvailable || !access.presignedUrl) {
        toast.error("아직 eBook이 준비되지 않은 도서입니다.");
        return;
      }
      setEbookUrl(access.presignedUrl);
      setActiveBook(book);
    } catch (err) {
      const code = err?.code;
      toast.error(
        code === "EBOOK_OWNERSHIP_REQUIRED"
          ? "구매 확정된 도서만 열람할 수 있어요."
          : "eBook을 열지 못했습니다. 잠시 후 다시 시도해주세요.",
      );
    } finally {
      setOpeningBookId(null);
    }
  };

  const handleClose = () => {
    setActiveBook(null);
    setEbookUrl(null);
  };

  return (
    <section className="mt-6 rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <h2 className="font-display mb-1 text-lg text-[var(--color-forest)]">내 이북 보관함</h2>

      <div className="pt-5">
        {ebooks === null ? (
          <TabSkeleton />
        ) : loadError ? (
          <EmptyState message="이북 보관함을 불러오지 못했습니다. 잠시 후 다시 시도해주세요." />
        ) : ebooks.length === 0 ? (
          <EmptyState message="구매한 eBook이 없어요. eBook이 있는 도서를 구매하면 여기서 바로 읽을 수 있어요." />
        ) : (
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {ebooks.map((book) => (
              <li key={book.id}>
                <button
                  type="button"
                  onClick={() => handleOpen(book)}
                  disabled={openingBookId === book.id}
                  className="flex w-full items-center gap-3 rounded-xl border border-[var(--color-forest)]/10 p-4 text-left transition-colors hover:bg-[var(--color-forest)]/5 disabled:opacity-60"
                >
                  <BookActivityTitle
                    title={book.title}
                    detail={openingBookId === book.id ? "여는 중..." : "📱 eBook 보기"}
                    coverImageUrl={book.coverImageUrl}
                  />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <EbookViewer
        isOpen={Boolean(activeBook)}
        onClose={handleClose}
        url={ebookUrl}
        title={activeBook?.title}
        bookId={activeBook?.id}
      />
    </section>
  );
}

```

- [ ] **Step 4: 타입/린트 확인**

Run: `cd frontend && npx tsc --noEmit`
Expected: 에러 없음

Run: `cd frontend && npx eslint src/pages/MyPage.jsx src/api/books.ts src/mocks/books.ts`
Expected: 에러 없음

Run: `cd frontend && npx prettier --check src/pages/MyPage.jsx src/api/books.ts src/mocks/books.ts`
Expected: "All matched files use Prettier code style!" — 아니면 `npx prettier --write` 후 재확인

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/MyPage.jsx
git commit -m "feat: 마이페이지에 '내 이북 보관함' 섹션 추가"
```

---

## Task 8: 로컬 docker-compose e2e 검증

**Files:** 없음(검증 전용 태스크, 코드 변경 없음)

이 태스크는 완료 기준의 "로컬 docker compose 환경에서 실제 로그인 후 e2e 확인"을 만족시키기 위한 수동 절차다. 프론트에 테스트 프레임워크가 없으므로 이 태스크가 유일한 종단 검증이다.

- [ ] **Step 1: 전체 스택 기동**

```bash
docker compose down -v && docker compose up --build -d
curl http://localhost:8081/actuator/health   # catalog — UP 확인
```

- [ ] **Step 2: 테스트 계정으로 회원가입 후 로그인**

프론트(`http://localhost:3000` 등, `docker-compose.yml`의 실제 포트 확인)에서 새 계정으로 회원가입 → 로그인한다. 로컬은 `AWS_COGNITO_USER_POOL_ID=mock`(기본값)이라 실제 AWS 없이 member-service 목업 인증으로 처리된다.

- [ ] **Step 3: 이 테스트 계정에 book_id 101(프랑켄슈타인) 구매 확정 기록을 심는다**

실제 결제(KakaoPay) 없이 소유권 검증을 로컬에서 확인하려면, 방금 만든 계정의 `member_id`(Cognito sub)를 찾아 `review_permissions`에 데모 행을 하나 추가한다 — `db/postgres/90-demo-data.sql`은 다른 작업으로 이미 수정 중이므로 건드리지 말고, `psql`로 직접 넣는다:

```bash
# member_id(=sub) 확인
docker compose exec postgres psql -U postgres -d appdb -c \
  "SELECT member_id, email FROM member_db.members ORDER BY created_at DESC LIMIT 5;"

# 위에서 확인한 member_id로 교체해서 실행 (book_id=101, order_item_id는 review_permissions PK 일부라 임의의 미사용 값)
docker compose exec postgres psql -U postgres -d appdb -c \
  "INSERT INTO catalog_db.review_permissions (member_id, order_item_id, book_id, nickname) VALUES ('<위에서 확인한 member_id>', 90001, 101, '로컬테스트') ON CONFLICT (member_id, order_item_id) DO NOTHING;"
```

- [ ] **Step 4: 마이페이지에서 확인**

`/mypage`로 이동해 "내 이북 보관함"에 "프랑켄슈타인"만 보이는지 확인한다(다른 도서는 epub이 없으므로 안 보여야 정상). 클릭 → `EbookViewer`가 바로 열리는지, 상세 페이지를 거치지 않는지 확인한다. 몇 페이지 넘기고 닫은 뒤 다시 열어 이어읽기 위치가 유지되는지 확인한다(기존 `useReadingProgress` 로직 — 새로 만든 코드가 아니므로 정상이면 이 부분은 회귀 없음의 증거).

- [ ] **Step 5: 소유권 차단 확인**

브라우저 개발자 도구 네트워크 탭 또는 curl로, **구매하지 않은** book_id(예: 102 — 위에서 101만 심었으므로)에 대해 직접 호출해본다:

```bash
# 위 계정의 JWT access token은 로그인 후 localStorage(authStorage)에서 확인
curl -s http://localhost:8081/api/catalog/books/102/ebook -H "Authorization: Bearer <token>" | jq
```

`success: false`, `error.code: "EBOOK_OWNERSHIP_REQUIRED"`, HTTP 403이 나오는지 확인한다. 프론트에서도 "내 이북 보관함"에 102(앨리스)는 애초에 목록에 나타나지 않아야 한다(구매 안 했으므로).

- [ ] **Step 6: 기존 Catalog 진입점 회귀 확인**

`/books/101` 상세 페이지에서도 기존처럼 "ebook 보기" 버튼으로 정상 열람되는지 확인한다(같은 백엔드 엔드포인트를 쓰므로 소유권 검증이 적용되지만, 101은 이미 구매 확정을 심어뒀으므로 정상 동작해야 한다).

- [ ] **Step 7: 정리(선택)**

로컬 검증용으로 심은 `review_permissions` 데모 행은 `docker compose down -v` 시 볼륨과 함께 사라지므로 별도 정리가 필요 없다.

---

## Self-Review 체크리스트 (실행 전 참고용 — 이미 반영됨)

- **완료 기준 커버리지**: ①마이페이지 목록 노출 → Task 6+7. ②클릭 시 Catalog 미경유 직행 → Task 7(모달, 라우트 이동 없음). ③이어읽기 위치 유지 → `EbookViewer`/`useReadingProgress` 재사용, 변경 없음(Task 7에서 확인만). ④타인 bookId 차단 → Task 1~3(서버 측 소유권 검증). ⑤ESLint/Prettier + 로컬 e2e → Task 7 Step 4, Task 8.
- **플레이스홀더 없음**: 모든 코드 블록은 실제로 실행 가능한 완성 코드다.
- **타입/시그니처 일관성**: `EbookService.getAccess(Long, String)`로 시그니처를 바꾸고 `EbookController`·`EbookServiceTest` 양쪽 모두에서 동일하게 갱신했다. `getMyEbooks(String): List<BookSummaryResponse>`는 `EbookService`(Task 3)→`MemberBookQueryController`(Task 4)→프론트 `getMyEbooks()`(Task 6)까지 이름이 일관된다.
- **SA 문제(투자 4번)**: 코드/인프라 조사로 원인을 특정했으나 수정은 이 plan 범위 밖 — "조사 결과 요약"에 명시하고, 실행 전 오현님 확인을 별도로 권고했다. 이 부분은 절대 추측으로 건드리지 않았다.

## Execution Handoff

Plan 저장 위치: `docs/superpowers/plans/2026-08-27-mypage-ebook-shelf.md`

Task 1~4(백엔드)는 서로 순서 의존성이 있다(Task 3이 Task 1·2를 소비, Task 4가 Task 3을 소비). Task 5(계약)는 Task 4 이후 아무 때나. Task 6(프론트 API)은 Task 4 완료 후(엔드포인트 존재 확인 목적, 실제로는 프론트 코드만으로도 컴파일된다). Task 7(UI)은 Task 6 이후. Task 8(e2e)은 전부 끝난 뒤.

두 가지 실행 방식 중 선택해 진행하면 된다:

1. **Subagent-Driven(권장)** — 태스크마다 새 subagent를 붙여 구현시키고 태스크 사이마다 리뷰
2. **Inline Execution** — 이 세션에서 순서대로 배치 실행, 체크포인트마다 확인
