package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.AdminBookCreateRequest;
import com.bookeatinglion.book.dto.AdminBookResponse;
import com.bookeatinglion.book.dto.AdminBookUpdateRequest;
import com.bookeatinglion.book.exception.CatalogConflictException;
import com.bookeatinglion.book.port.BookIngestPublisher;
import com.bookeatinglion.book.repository.BookRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AdminBookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BookIngestPublisher bookIngestPublisher;

    @InjectMocks
    private AdminBookService adminBookService;

    @Test
    void 활성_카테고리에_도서를_등록한다() {
        AdminBookCreateRequest request = new AdminBookCreateRequest(
                "스프링",
                "저자",
                "출판사",
                "9791100000001",
                "IT/컴퓨터",
                20000,
                "cover.jpg",
                "설명",
                "상세 줄거리",
                "ebooks/spring.epub",
                SaleStatus.ON_SALE,
                LocalDate.of(2026, 1, 1));
        when(categoryService.getActiveCategoryName("IT/컴퓨터")).thenReturn("IT/컴퓨터");
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminBookResponse result = adminBookService.create(request);

        assertThat(result.title()).isEqualTo("스프링");
        assertThat(result.category()).isEqualTo("IT/컴퓨터");
    }

    @Test
    void epubS3Key가_있는_신간_등록은_인제스트_이벤트를_발행한다() {
        AdminBookCreateRequest request = new AdminBookCreateRequest(
                "스프링",
                "저자",
                "출판사",
                "9791100000001",
                "IT/컴퓨터",
                20000,
                "cover.jpg",
                "설명",
                "상세 줄거리",
                "epubs/uuid_spring.epub",
                SaleStatus.ON_SALE,
                LocalDate.of(2026, 1, 1));
        when(categoryService.getActiveCategoryName("IT/컴퓨터")).thenReturn("IT/컴퓨터");
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminBookService.create(request);

        verify(bookIngestPublisher).publish(null, "스프링", "IT/컴퓨터", "epubs/uuid_spring.epub");
    }

    @Test
    void epubS3Key가_없는_신간_등록은_인제스트_이벤트를_발행하지_않는다() {
        AdminBookCreateRequest request = new AdminBookCreateRequest(
                "스프링", "저자", "출판사", "9791100000001", "IT/컴퓨터", 20000, null, null, null, null, null, null);
        when(categoryService.getActiveCategoryName("IT/컴퓨터")).thenReturn("IT/컴퓨터");
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminBookService.create(request);

        verify(bookIngestPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void 기존_EPUB_도서를_인제스트_큐에_전체_재발행한다() {
        Book ebook = Book.builder()
                .title("앨리스")
                .author("루이스 캐럴")
                .publisher("출판사")
                .isbn("9791100000001")
                .category("소설")
                .price(10000)
                .epubS3Key("epubs/alice.epub")
                .saleStatus(SaleStatus.ON_SALE)
                .salesCount(0)
                .build();
        when(bookRepository.findByEpubS3KeyIsNotNullAndIsDeletedFalse()).thenReturn(List.of(ebook));
        when(bookIngestPublisher.publish(null, "앨리스", "소설", "epubs/alice.epub")).thenReturn(true);

        int count = adminBookService.reindexEbooks();

        assertThat(count).isEqualTo(1);
        verify(bookIngestPublisher).publish(null, "앨리스", "소설", "epubs/alice.epub");
    }

    @Test
    void EPUB_인제스트_발행_실패는_성공_건수에서_제외한다() {
        Book ebook = Book.builder()
                .title("실패 도서")
                .author("저자")
                .publisher("출판사")
                .isbn("9791100000002")
                .category("소설")
                .price(10000)
                .epubS3Key("epubs/failure.epub")
                .saleStatus(SaleStatus.ON_SALE)
                .salesCount(0)
                .build();
        when(bookRepository.findByEpubS3KeyIsNotNullAndIsDeletedFalse()).thenReturn(List.of(ebook));
        when(bookIngestPublisher.publish(null, "실패 도서", "소설", "epubs/failure.epub"))
                .thenReturn(false);

        assertThat(adminBookService.reindexEbooks()).isZero();
    }

    @Test
    void 중복_ISBN은_등록하지_않는다() {
        AdminBookCreateRequest request = new AdminBookCreateRequest(
                "스프링", "저자", "출판사", "9791100000001", "IT/컴퓨터", 20000, null, null, null, null, null, null);
        when(bookRepository.existsByIsbn(request.isbn())).thenReturn(true);

        assertThatThrownBy(() -> adminBookService.create(request)).isInstanceOf(CatalogConflictException.class);
    }

    @Test
    void 전달된_필드만_수정한다() {
        Book book = Book.builder()
                .title("기존 제목")
                .author("저자")
                .publisher("출판사")
                .isbn("9791100000001")
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .salesCount(0)
                .build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        AdminBookUpdateRequest request =
                new AdminBookUpdateRequest("변경 제목", null, null, null, null, 15000, null, null, null, null, null, null);

        AdminBookResponse result = adminBookService.update(1L, request);

        assertThat(result.title()).isEqualTo("변경 제목");
        assertThat(result.author()).isEqualTo("저자");
        assertThat(result.price()).isEqualTo(15000);
    }

    @Test
    void 도서는_소프트_삭제한다() {
        Book book = Book.builder()
                .title("책")
                .author("저자")
                .publisher("출판사")
                .isbn("9791100000001")
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .salesCount(0)
                .build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        adminBookService.delete(1L);

        assertThat(book.isDeleted()).isTrue();
        assertThat(book.getSaleStatus()).isEqualTo(SaleStatus.STOPPED);
        assertThat(book.getDeletedAt()).isNotNull();
    }
}
