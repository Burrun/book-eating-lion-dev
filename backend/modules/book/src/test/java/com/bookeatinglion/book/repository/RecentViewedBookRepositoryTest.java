package com.bookeatinglion.book.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RecentViewedBook;
import com.bookeatinglion.book.domain.SaleStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

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
                .title("최근본책1")
                .author("저자")
                .publisher("출판사")
                .isbn("9791100000051")
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .salesCount(0)
                .build());
        book2 = bookRepository.save(Book.builder()
                .title("최근본책2")
                .author("저자")
                .publisher("출판사")
                .isbn("9791100000052")
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .salesCount(0)
                .build());
    }

    @Test
    void 최근_본_기록을_저장하고_조회한다() {
        recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId("member-1")
                .book(book1)
                .viewedAt(LocalDateTime.now())
                .build());

        assertThat(recentViewedBookRepository.findByMemberIdAndBook_BookId("member-1", book1.getBookId()))
                .isPresent();
    }

    @Test
    void 같은_회원_같은_책은_중복_기록될_수_없다() {
        recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId("member-1")
                .book(book1)
                .viewedAt(LocalDateTime.now())
                .build());

        assertThrows(
                Exception.class,
                () -> recentViewedBookRepository.save(RecentViewedBook.builder()
                        .memberId("member-1")
                        .book(book1)
                        .viewedAt(LocalDateTime.now())
                        .build()));
    }

    @Test
    void 최근_본_순으로_조회한다() {
        RecentViewedBook older = recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId("member-1")
                .book(book1)
                .viewedAt(LocalDateTime.now().minusDays(1))
                .build());
        recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId("member-1")
                .book(book2)
                .viewedAt(LocalDateTime.now())
                .build());

        List<RecentViewedBook> result =
                recentViewedBookRepository.findByMemberIdOrderByViewedAtDesc("member-1", PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBook().getBookId()).isEqualTo(book2.getBookId());
        assertThat(result.get(1).getRecentBookId()).isEqualTo(older.getRecentBookId());
    }

    @Test
    void touch로_viewedAt을_갱신한다() {
        RecentViewedBook recentViewedBook = recentViewedBookRepository.save(RecentViewedBook.builder()
                .memberId("member-1")
                .book(book1)
                .viewedAt(LocalDateTime.now().minusDays(1))
                .build());
        LocalDateTime newTime = LocalDateTime.now();

        recentViewedBook.touch(newTime);

        assertThat(recentViewedBook.getViewedAt()).isEqualTo(newTime);
    }
}
