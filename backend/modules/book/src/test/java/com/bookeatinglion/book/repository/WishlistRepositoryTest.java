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
