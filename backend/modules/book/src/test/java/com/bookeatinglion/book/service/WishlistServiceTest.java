package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.domain.Wishlist;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.WishlistRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                .title("책")
                .author("저자")
                .publisher("출판사")
                .isbn("978120000" + id)
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .salesCount(0)
                .build();
        Field idField = Book.class.getDeclaredField("bookId");
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
    }

    @Test
    void 찜하지_않은_책을_찜한다() throws Exception {
        Book book = book(1L);
        when(wishlistRepository.existsByMemberIdAndBook_BookIdAndBook_IsDeletedFalse("member-1", 1L))
                .thenReturn(false);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));

        wishlistService.addWishlist(1L, "member-1");

        verify(wishlistRepository, times(1)).save(any());
    }

    @Test
    void 이미_찜한_책을_다시_찜해도_중복_저장하지_않는다() throws Exception {
        when(wishlistRepository.existsByMemberIdAndBook_BookIdAndBook_IsDeletedFalse("member-1", 1L))
                .thenReturn(true);

        wishlistService.addWishlist(1L, "member-1");

        verify(wishlistRepository, never()).save(any());
        verify(bookRepository, never()).findByBookIdAndIsDeletedFalse(any());
    }

    @Test
    void 존재하지_않는_책을_찜하면_예외를_던진다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.addWishlist(999L, "member-1"))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 삭제된_책을_이미_찜했어도_다시_추가할_수_없다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.addWishlist(1L, "member-1")).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 찜을_삭제한다() {
        wishlistService.removeWishlist(1L, "member-1");

        verify(wishlistRepository, times(1)).deleteByMemberIdAndBook_BookId("member-1", 1L);
    }

    @Test
    void 내_찜_목록을_책_요약_정보로_조회한다() throws Exception {
        Book book = book(1L);
        when(wishlistRepository.findByMemberIdAndBook_IsDeletedFalseOrderByCreatedAtDesc("member-1"))
                .thenReturn(List.of(
                        Wishlist.builder().memberId("member-1").book(book).build()));

        List<BookSummaryResponse> result = wishlistService.getMyWishlist("member-1");

        assertThat(result).extracting(BookSummaryResponse::title).containsExactly("책");
    }
}
