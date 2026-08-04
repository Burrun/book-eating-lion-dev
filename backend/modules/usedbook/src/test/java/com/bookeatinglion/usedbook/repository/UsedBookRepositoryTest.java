package com.bookeatinglion.usedbook.repository;

import com.bookeatinglion.usedbook.UsedBookModuleTestApplication;
import com.bookeatinglion.usedbook.domain.UsedBook;
import com.bookeatinglion.usedbook.domain.UsedBookCondition;
import com.bookeatinglion.usedbook.domain.UsedBookStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = UsedBookModuleTestApplication.class)
class UsedBookRepositoryTest {

    @Autowired
    private UsedBookRepository usedBookRepository;

    @BeforeEach
    void setUp() {
        usedBookRepository.save(usedBook("9791100000001", "스프링 입문", UsedBookStatus.ON_SALE));
        usedBookRepository.save(usedBook("9791100000001", "스프링 입문(밑줄 있음)", UsedBookStatus.SOLD_OUT));
        usedBookRepository.save(usedBook("9791100000002", "자바의 정석", UsedBookStatus.ON_SALE));
    }

    private UsedBook usedBook(String isbn, String title, UsedBookStatus status) {
        UsedBook usedBook = UsedBook.builder()
                .sellerId("seller-1")
                .isbn(isbn)
                .title(title)
                .author("저자")
                .publisher("출판사")
                .price(10000)
                .condition(UsedBookCondition.GOOD)
                .description("설명")
                .imageUrls(List.of("https://example.com/1.jpg"))
                .build();
        if (status != UsedBookStatus.ON_SALE) {
            ReflectionTestUtils.setField(usedBook, "status", status);
        }
        return usedBook;
    }

    @Test
    void 필터_없이_조회하면_전체를_반환한다() {
        Page<UsedBook> result = usedBookRepository.search(null, null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void isbn으로_필터링한다() {
        Page<UsedBook> result = usedBookRepository.search("9791100000001", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(UsedBook::getTitle)
                .containsExactlyInAnyOrder("스프링 입문", "스프링 입문(밑줄 있음)");
    }

    @Test
    void 상태로_필터링한다() {
        Page<UsedBook> result = usedBookRepository.search(null, UsedBookStatus.SOLD_OUT, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(UsedBook::getTitle)
                .containsExactly("스프링 입문(밑줄 있음)");
    }

    @Test
    void 키워드로_제목을_검색한다() {
        Page<UsedBook> result = usedBookRepository.search(null, null, "자바", PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(UsedBook::getTitle)
                .containsExactly("자바의 정석");
    }
}
