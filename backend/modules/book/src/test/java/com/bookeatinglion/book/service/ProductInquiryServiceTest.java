package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ProductInquiry;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.InquiryAnswerRequest;
import com.bookeatinglion.book.dto.InquiryCreateRequest;
import com.bookeatinglion.book.dto.InquiryUpdateRequest;
import com.bookeatinglion.book.exception.InquiryAccessDeniedException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ProductInquiryRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductInquiryServiceTest {

    @Mock
    ProductInquiryRepository inquiryRepository;

    @Mock
    BookRepository bookRepository;

    @InjectMocks
    ProductInquiryService inquiryService;

    @Test
    void 공개_도서에_문의를_작성한다() throws Exception {
        Book book = book(1L);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(inquiryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = inquiryService.create(1L, "member-1", new InquiryCreateRequest("배송 문의", "언제 오나요?", true));

        assertThat(result.memberId()).isEqualTo("member-1");
        assertThat(result.privateInquiry()).isTrue();
        assertThat(result.status().name()).isEqualTo("WAITING");
    }

    @Test
    void 작성자가_아니면_문의_수정을_거부한다() throws Exception {
        ProductInquiry inquiry = inquiry(10L, book(1L), "member-1");
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryService.update(10L, "member-2", new InquiryUpdateRequest("수정", "내용", false)))
                .isInstanceOf(InquiryAccessDeniedException.class);
    }

    @Test
    void 작성자_삭제는_물리삭제하지_않고_소프트삭제한다() throws Exception {
        ProductInquiry inquiry = inquiry(10L, book(1L), "member-1");
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

        inquiryService.delete(10L, "member-1");

        assertThat(inquiry.isDeleted()).isTrue();
        verify(inquiryRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void 관리자가_문의에_답변한다() throws Exception {
        ProductInquiry inquiry = inquiry(10L, book(1L), "member-1");
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

        var result = inquiryService.answer(10L, "admin-sub", new InquiryAnswerRequest("내일 입고됩니다."));

        assertThat(result.answer()).isEqualTo("내일 입고됩니다.");
        assertThat(result.answeredBy()).isEqualTo("admin-sub");
        assertThat(result.status().name()).isEqualTo("ANSWERED");
    }

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책")
                .author("저자")
                .publisher("출판사")
                .isbn("9781100000001")
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .build();
        setField(book, Book.class, "bookId", id);
        return book;
    }

    private ProductInquiry inquiry(Long id, Book book, String memberId) throws Exception {
        ProductInquiry inquiry = ProductInquiry.builder()
                .book(book)
                .memberId(memberId)
                .title("문의")
                .content("내용")
                .privateInquiry(false)
                .build();
        setField(inquiry, ProductInquiry.class, "inquiryId", id);
        return inquiry;
    }

    private void setField(Object target, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
