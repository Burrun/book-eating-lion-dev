package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.exception.EbookOwnershipRequiredException;
import com.bookeatinglion.book.port.EbookStoragePort;
import com.bookeatinglion.book.port.MemberSubscriptionPort;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewPermissionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EbookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ReviewPermissionRepository reviewPermissionRepository;

    @Mock
    private MemberSubscriptionPort memberSubscriptionPort;

    @Mock
    private EbookStoragePort ebookStoragePort;

    private EbookService ebookService;

    @BeforeEach
    void setUp() {
        ebookService = new EbookService(
                bookRepository,
                reviewPermissionRepository,
                memberSubscriptionPort,
                ebookStoragePort,
                Duration.ofMinutes(10),
                Duration.ofMinutes(10));
    }

    @Test
    void ebook이_없는_도서는_구매_구독_여부와_무관하게_미지원으로_응답한다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book(null)));

        EbookAccessResponse result = ebookService.getAccess(1L, "member-1");

        assertThat(result.ebookAvailable()).isFalse();
        assertThat(result.presignedUrl()).isNull();
        verifyNoInteractions(ebookStoragePort);
        verifyNoInteractions(reviewPermissionRepository);
        verifyNoInteractions(memberSubscriptionPort);
    }

    @Test
    void 구매_확정한_회원에게는_구독_조회_없이_열람_URL을_발급한다() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book("ebooks/alice.epub")));
        when(reviewPermissionRepository.existsByIdMemberIdAndBookId("member-1", 1L))
                .thenReturn(true);
        when(ebookStoragePort.createReadUrl("ebooks/alice.epub", Duration.ofMinutes(10)))
                .thenReturn(new EbookStoragePort.ReadUrl("https://signed.example/alice", expiresAt));

        EbookAccessResponse result = ebookService.getAccess(1L, "member-1");

        assertThat(result.ebookAvailable()).isTrue();
        assertThat(result.presignedUrl()).isEqualTo("https://signed.example/alice");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.purchased()).isTrue();
        // 구매 확정만으로 통과하므로 && 단락 평가로 구독 조회는 아예 안 나가야 한다.
        verifyNoInteractions(memberSubscriptionPort);
    }

    @Test
    void 구독_중이면_구매하지_않아도_열람_URL을_발급한다() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book("ebooks/alice.epub")));
        when(reviewPermissionRepository.existsByIdMemberIdAndBookId("member-3", 1L))
                .thenReturn(false);
        when(memberSubscriptionPort.isSubscribed("member-3")).thenReturn(true);
        when(ebookStoragePort.createReadUrl("ebooks/alice.epub", Duration.ofMinutes(10)))
                .thenReturn(new EbookStoragePort.ReadUrl("https://signed.example/alice", expiresAt));

        EbookAccessResponse result = ebookService.getAccess(1L, "member-3");

        assertThat(result.ebookAvailable()).isTrue();
        assertThat(result.presignedUrl()).isEqualTo("https://signed.example/alice");
        // 읽을 수는 있어도 구매는 아니다 — 뷰어가 이 값으로 사자 진입점을 숨긴다.
        assertThat(result.purchased()).isFalse();
    }

    @Test
    void 구매하지_않고_구독도_아닌_회원은_403에_해당하는_예외를_받는다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book("ebooks/alice.epub")));
        when(reviewPermissionRepository.existsByIdMemberIdAndBookId("member-2", 1L))
                .thenReturn(false);
        when(memberSubscriptionPort.isSubscribed("member-2")).thenReturn(false);

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

    @Test
    void 구매_이력이_없으면_빈_목록을_반환하고_book_repository는_호출하지_않는다() {
        when(memberSubscriptionPort.isSubscribed("member-1")).thenReturn(false);
        when(reviewPermissionRepository.findByIdMemberId("member-1")).thenReturn(List.of());

        List<BookSummaryResponse> result = ebookService.getMyEbooks("member-1");

        assertThat(result).isEmpty();
        verifyNoInteractions(bookRepository);
    }

    @Test
    void 동일한_도서를_중복_구매해도_중복_제거된_ID로_한_번만_조회한다() {
        when(memberSubscriptionPort.isSubscribed("member-1")).thenReturn(false);
        ReviewPermission first = reviewPermission("member-1", 1L, 101L);
        ReviewPermission second = reviewPermission("member-1", 2L, 101L);
        when(reviewPermissionRepository.findByIdMemberId("member-1")).thenReturn(List.of(first, second));
        Book book = book(101L, "ebooks/alice.epub");
        when(bookRepository.findByBookIdInAndEpubS3KeyIsNotNullAndIsDeletedFalse(List.of(101L)))
                .thenReturn(List.of(book));

        List<BookSummaryResponse> result = ebookService.getMyEbooks("member-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(101L);
        assertThat(result.get(0).title()).isEqualTo("앨리스");
    }

    @Test
    void 구독_중이면_구매_여부와_무관하게_eBook_보유_도서_전체를_반환한다() {
        when(memberSubscriptionPort.isSubscribed("member-3")).thenReturn(true);
        Book purchased = book(101L, "ebooks/alice.epub");
        Book neverPurchased = book(102L, "ebooks/frankenstein.epub");
        when(bookRepository.findByEpubS3KeyIsNotNullAndIsDeletedFalse()).thenReturn(List.of(purchased, neverPurchased));

        List<BookSummaryResponse> result = ebookService.getMyEbooks("member-3");

        assertThat(result).hasSize(2).extracting(BookSummaryResponse::id).containsExactlyInAnyOrder(101L, 102L);
        // 구독 중이면 구매 확정 이력을 조회할 필요가 없다 — 전체 목록이 곧 정답이다.
        verifyNoInteractions(reviewPermissionRepository);
    }

    private ReviewPermission reviewPermission(String memberId, Long orderItemId, Long bookId) {
        return new ReviewPermission(memberId, orderItemId, bookId, "닉네임", LocalDateTime.now());
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

    private Book book(Long id, String epubS3Key) {
        Book book = book(epubS3Key);
        ReflectionTestUtils.setField(book, "bookId", id);
        return book;
    }
}
