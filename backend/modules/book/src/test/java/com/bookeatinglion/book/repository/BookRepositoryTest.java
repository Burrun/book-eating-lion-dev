package com.bookeatinglion.book.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.save(
                book("스프링 입문", "김스프링", "IT/컴퓨터", LocalDate.of(2026, 1, 1), 10, SaleStatus.ON_SALE, "9791100000011"));
        bookRepository.save(
                book("자바의 정석", "남궁성", "IT/컴퓨터", LocalDate.of(2026, 3, 1), 100, SaleStatus.ON_SALE, "9791100000012"));
        bookRepository.save(
                book("어린 왕자", "생텍쥐페리", "소설", LocalDate.of(2026, 2, 1), 50, SaleStatus.ON_SALE, "9791100000013"));
        bookRepository.save(
                book("절판된 책", "익명", "소설", LocalDate.of(2020, 1, 1), 5, SaleStatus.STOPPED, "9791100000014"));
    }

    private Book book(
            String title,
            String author,
            String category,
            LocalDate publishedDate,
            int salesCount,
            SaleStatus saleStatus,
            String isbn) {
        return Book.builder()
                .title(title)
                .author(author)
                .publisher("출판사")
                .isbn(isbn)
                .category(category)
                .price(10000)
                .saleStatus(saleStatus)
                .publishedDate(publishedDate)
                .salesCount(salesCount)
                .build();
    }

    @Test
    void 카테고리로_필터링한다() {
        Page<Book> result = bookRepository.findByCategoryAndIsDeletedFalse("IT/컴퓨터", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Book::getTitle).containsExactlyInAnyOrder("스프링 입문", "자바의 정석");
    }

    @Test
    void 제목이나_저자로_검색한다() {
        Page<Book> byTitle = bookRepository.search("스프링", SaleStatus.STOPPED, PageRequest.of(0, 10));
        assertThat(byTitle.getContent()).extracting(Book::getTitle).containsExactly("스프링 입문");

        Page<Book> byAuthor = bookRepository.search("생텍쥐페리", SaleStatus.STOPPED, PageRequest.of(0, 10));
        assertThat(byAuthor.getContent()).extracting(Book::getTitle).containsExactly("어린 왕자");
    }

    @Test
    void 판매중지_도서는_목록과_검색에서_빠진다() {
        // 정기구독권(book_id 9001)이 books 한 행으로 들어가 있고 sale_status 가 STOPPED 다.
        // 도서 목록에 "책"인 척 섞여 나오던 것을 판매 상태로 거른다.
        bookRepository.save(book(
                "책 먹는 사자 정기구독 (월간)",
                "책 먹는 사자",
                "구독",
                LocalDate.of(2026, 1, 1),
                0,
                SaleStatus.STOPPED,
                "9791100009001"));

        Page<Book> all = bookRepository.findBySaleStatusNotAndIsDeletedFalse(SaleStatus.STOPPED, PageRequest.of(0, 50));
        assertThat(all.getContent()).extracting(Book::getTitle).doesNotContain("책 먹는 사자 정기구독 (월간)");

        Page<Book> byCategory = bookRepository.findByCategoryAndSaleStatusNotAndIsDeletedFalse(
                "구독", SaleStatus.STOPPED, PageRequest.of(0, 10));
        assertThat(byCategory.getContent()).isEmpty();

        Page<Book> bySearch = bookRepository.search("정기구독", SaleStatus.STOPPED, PageRequest.of(0, 10));
        assertThat(bySearch.getContent()).isEmpty();

        // 관리자 목록과 상세 조회는 그대로 보여야 한다 - 구독권 주문이 이 경로를 탄다.
        assertThat(bookRepository.findByIsDeletedFalse(PageRequest.of(0, 50)).getContent())
                .extracting(Book::getTitle)
                .contains("책 먹는 사자 정기구독 (월간)");
    }

    @Test
    void 판매량_내림차순으로_정렬한다() {
        List<Book> result = bookRepository.findBySaleStatusAndIsDeletedFalseOrderBySalesCountDesc(
                SaleStatus.ON_SALE, PageRequest.of(0, 2));

        assertThat(result).extracting(Book::getTitle).containsExactly("자바의 정석", "어린 왕자");
    }

    @Test
    void 출간일_내림차순으로_정렬한다() {
        List<Book> result = bookRepository.findBySaleStatusAndIsDeletedFalseOrderByPublishedDateDesc(
                SaleStatus.ON_SALE, PageRequest.of(0, 2));

        assertThat(result).extracting(Book::getTitle).containsExactly("자바의 정석", "어린 왕자");
    }
}
