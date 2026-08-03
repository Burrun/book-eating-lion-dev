package com.bookeatinglion.book.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = com.bookeatinglion.book.BookModuleTestApplication.class)
class BookPersistenceTest {

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void 모든_필드가_저장되고_조회된다() {
        Book book = Book.builder()
                .title("클라우드 엔지니어링 교재")
                .author("홍길동")
                .publisher("라이언출판사")
                .isbn("9791100000001")
                .category("IT/컴퓨터")
                .price(25000)
                .stockQuantity(100)
                .coverImageUrl("https://example.com/cover.jpg")
                .description("짧은 소개")
                .detailedSynopsis("상세 줄거리 본문")
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.of(2026, 1, 15))
                .salesCount(42)
                .build();

        entityManager.persist(book);
        entityManager.flush();
        entityManager.clear();

        Book found = entityManager.find(Book.class, book.getId());

        assertThat(found.getTitle()).isEqualTo("클라우드 엔지니어링 교재");
        assertThat(found.getAuthor()).isEqualTo("홍길동");
        assertThat(found.getPublisher()).isEqualTo("라이언출판사");
        assertThat(found.getIsbn()).isEqualTo("9791100000001");
        assertThat(found.getCategory()).isEqualTo("IT/컴퓨터");
        assertThat(found.getPrice()).isEqualTo(25000);
        assertThat(found.getStockQuantity()).isEqualTo(100);
        assertThat(found.getCoverImageUrl()).isEqualTo("https://example.com/cover.jpg");
        assertThat(found.getDescription()).isEqualTo("짧은 소개");
        assertThat(found.getDetailedSynopsis()).isEqualTo("상세 줄거리 본문");
        assertThat(found.getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
        assertThat(found.getPublishedDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(found.getSalesCount()).isEqualTo(42);
    }

    @Test
    void isbn은_유니크_제약이_걸려있다() {
        Book book1 = Book.builder()
                .title("책1").author("저자").publisher("출판사").isbn("9791100000099")
                .category("소설").price(10000).stockQuantity(1)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();
        Book book2 = Book.builder()
                .title("책2").author("저자").publisher("출판사").isbn("9791100000099")
                .category("소설").price(10000).stockQuantity(1)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.now()).salesCount(0)
                .build();

        entityManager.persist(book1);
        entityManager.flush();

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> entityManager.persist(book2)
        );
    }
}
