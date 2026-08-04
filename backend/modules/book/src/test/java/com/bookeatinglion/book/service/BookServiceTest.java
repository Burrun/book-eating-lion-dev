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
        Field idField = Book.class.getDeclaredField("bookId");
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
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
