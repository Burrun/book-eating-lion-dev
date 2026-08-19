package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.AdminBookCreateRequest;
import com.bookeatinglion.book.dto.AdminBookResponse;
import com.bookeatinglion.book.dto.AdminBookUpdateRequest;
import com.bookeatinglion.book.exception.CatalogConflictException;
import com.bookeatinglion.book.repository.BookRepository;
import java.time.LocalDate;
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
