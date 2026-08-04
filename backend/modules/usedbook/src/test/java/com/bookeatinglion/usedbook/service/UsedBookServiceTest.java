package com.bookeatinglion.usedbook.service;

import com.bookeatinglion.usedbook.domain.UsedBook;
import com.bookeatinglion.usedbook.domain.UsedBookCondition;
import com.bookeatinglion.usedbook.dto.UsedBookCreateRequest;
import com.bookeatinglion.usedbook.dto.UsedBookResponse;
import com.bookeatinglion.usedbook.dto.UsedBookSummaryResponse;
import com.bookeatinglion.usedbook.exception.UsedBookNotFoundException;
import com.bookeatinglion.usedbook.repository.UsedBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsedBookServiceTest {

    @Mock
    private UsedBookRepository usedBookRepository;

    @InjectMocks
    private UsedBookService usedBookService;

    private UsedBook usedBook(Long id) {
        UsedBook usedBook = UsedBook.builder()
                .sellerId("seller-1")
                .isbn("9791100000001")
                .title("스프링 입문")
                .author("저자")
                .publisher("출판사")
                .coverImageUrl("cover.jpg")
                .price(10000)
                .condition(UsedBookCondition.GOOD)
                .description("설명")
                .imageUrls(List.of("https://example.com/1.jpg"))
                .build();
        ReflectionTestUtils.setField(usedBook, "id", id);
        return usedBook;
    }

    @Test
    void 매물을_등록한다() {
        UsedBookCreateRequest request = new UsedBookCreateRequest(
                "9791100000001", "스프링 입문", "저자", "출판사", "cover.jpg",
                10000, UsedBookCondition.GOOD, "설명", List.of("https://example.com/1.jpg"));
        when(usedBookRepository.save(any(UsedBook.class))).thenReturn(usedBook(1L));

        UsedBookResponse response = usedBookService.createUsedBook("seller-1", request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sellerId()).isEqualTo("seller-1");
        assertThat(response.title()).isEqualTo("스프링 입문");

        ArgumentCaptor<UsedBook> captor = ArgumentCaptor.forClass(UsedBook.class);
        org.mockito.Mockito.verify(usedBookRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo("seller-1");
    }

    @Test
    void 목록을_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UsedBook> page = new PageImpl<>(List.of(usedBook(1L)));
        when(usedBookRepository.search(eq("9791100000001"), isNull(), isNull(), any())).thenReturn(page);

        Page<UsedBookSummaryResponse> result = usedBookService.getUsedBooks("9791100000001", null, null, pageable);

        assertThat(result.getContent()).extracting(UsedBookSummaryResponse::title).containsExactly("스프링 입문");
    }

    @Test
    void 상세_조회에_성공한다() {
        when(usedBookRepository.findById(1L)).thenReturn(Optional.of(usedBook(1L)));

        UsedBookResponse response = usedBookService.getUsedBook(1L);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_매물_조회시_예외를_던진다() {
        when(usedBookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedBookService.getUsedBook(999L))
                .isInstanceOf(UsedBookNotFoundException.class);
    }
}
