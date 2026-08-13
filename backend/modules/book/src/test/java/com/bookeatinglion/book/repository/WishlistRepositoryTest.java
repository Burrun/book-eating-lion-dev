package com.bookeatinglion.book.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.domain.Wishlist;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

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
                .title("찜용 책")
                .author("저자")
                .publisher("출판사")
                .isbn("9791100000041")
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .salesCount(0)
                .build());
    }

    @Test
    void 찜을_저장하고_회원_책_조합으로_조회한다() {
        wishlistRepository.save(
                Wishlist.builder().memberId("member-1").book(book).build());

        assertThat(wishlistRepository.findByMemberIdAndBook_BookId("member-1", book.getBookId()))
                .isPresent();
        assertThat(wishlistRepository.findByMemberIdAndBook_BookId("member-2", book.getBookId()))
                .isEmpty();
    }

    @Test
    void 삭제되지_않은_책의_찜만_존재하는_것으로_확인한다() {
        wishlistRepository.save(
                Wishlist.builder().memberId("member-1").book(book).build());

        assertThat(wishlistRepository.existsByMemberIdAndBook_BookIdAndBook_IsDeletedFalse(
                        "member-1", book.getBookId()))
                .isTrue();

        book.delete(LocalDateTime.now());
        bookRepository.flush();

        assertThat(wishlistRepository.existsByMemberIdAndBook_BookIdAndBook_IsDeletedFalse(
                        "member-1", book.getBookId()))
                .isFalse();
    }

    @Test
    void 같은_회원_같은_책은_중복_찜할_수_없다() {
        wishlistRepository.save(
                Wishlist.builder().memberId("member-1").book(book).build());

        assertThrows(
                Exception.class,
                () -> wishlistRepository.save(
                        Wishlist.builder().memberId("member-1").book(book).build()));
    }

    @Test
    void 회원의_찜_목록을_최신순으로_조회한다() {
        wishlistRepository.save(
                Wishlist.builder().memberId("member-1").book(book).build());

        List<Wishlist> result = wishlistRepository.findByMemberIdOrderByCreatedAtDesc("member-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBook().getBookId()).isEqualTo(book.getBookId());
    }

    @Test
    void 삭제된_책은_회원의_찜_목록에서_제외한다() {
        wishlistRepository.save(
                Wishlist.builder().memberId("member-1").book(book).build());
        book.delete(LocalDateTime.now());
        bookRepository.flush();

        List<Wishlist> result = wishlistRepository.findByMemberIdAndBook_IsDeletedFalseOrderByCreatedAtDesc("member-1");

        assertThat(result).isEmpty();
    }

    @Test
    void 찜을_삭제한다() {
        wishlistRepository.save(
                Wishlist.builder().memberId("member-1").book(book).build());

        wishlistRepository.deleteByMemberIdAndBook_BookId("member-1", book.getBookId());

        assertThat(wishlistRepository.findByMemberIdAndBook_BookId("member-1", book.getBookId()))
                .isEmpty();
    }

    @Test
    void 존재하지_않는_찜을_삭제해도_에러가_나지_않는다() {
        wishlistRepository.deleteByMemberIdAndBook_BookId("member-999", book.getBookId());
    }
}
