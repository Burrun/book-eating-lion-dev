package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.save(book("스프링 입문", "김스프링", "IT/컴퓨터",
                LocalDate.of(2026, 1, 1), 10, SaleStatus.ON_SALE, "9791100000011"));
        bookRepository.save(book("자바의 정석", "남궁성", "IT/컴퓨터",
                LocalDate.of(2026, 3, 1), 100, SaleStatus.ON_SALE, "9791100000012"));
        bookRepository.save(book("어린 왕자", "생텍쥐페리", "소설",
                LocalDate.of(2026, 2, 1), 50, SaleStatus.ON_SALE, "9791100000013"));
        bookRepository.save(book("절판된 책", "익명", "소설",
                LocalDate.of(2020, 1, 1), 5, SaleStatus.STOPPED, "9791100000014"));
    }

    private Book book(String title, String author, String category, LocalDate publishedDate,
                       int salesCount, SaleStatus saleStatus, String isbn) {
        return Book.builder()
                .title(title).author(author).publisher("출판사").isbn(isbn)
                .category(category).price(10000).stockQuantity(10)
                .saleStatus(saleStatus).publishedDate(publishedDate).salesCount(salesCount)
                .build();
    }

    @Test
    void 카테고리로_필터링한다() {
        Page<Book> result = bookRepository.findByCategory("IT/컴퓨터", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("스프링 입문", "자바의 정석");
    }

    @Test
    void 제목이나_저자로_검색한다() {
        Page<Book> byTitle = bookRepository.search("스프링", PageRequest.of(0, 10));
        assertThat(byTitle.getContent()).extracting(Book::getTitle).containsExactly("스프링 입문");

        Page<Book> byAuthor = bookRepository.search("생텍쥐페리", PageRequest.of(0, 10));
        assertThat(byAuthor.getContent()).extracting(Book::getTitle).containsExactly("어린 왕자");
    }

    @Test
    void 판매량_내림차순으로_정렬한다() {
        List<Book> result = bookRepository.findBySaleStatusOrderBySalesCountDesc(
                SaleStatus.ON_SALE, PageRequest.of(0, 2));

        assertThat(result).extracting(Book::getTitle)
                .containsExactly("자바의 정석", "어린 왕자");
    }

    @Test
    void 출간일_내림차순으로_정렬한다() {
        List<Book> result = bookRepository.findBySaleStatusOrderByPublishedDateDesc(
                SaleStatus.ON_SALE, PageRequest.of(0, 2));

        assertThat(result).extracting(Book::getTitle)
                .containsExactly("자바의 정석", "어린 왕자");
    }
}
