package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.domain.SearchHistory;
import com.bookeatinglion.book.port.RecommendationAiPort;
import com.bookeatinglion.book.port.RecommendationAiPort.RankedBook;
import com.bookeatinglion.book.port.RecommendationQueuePort;
import com.bookeatinglion.book.repository.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    BookRepository bookRepository;

    @Mock
    RecentViewedBookRepository recentRepository;

    @Mock
    WishlistRepository wishlistRepository;

    @Mock
    ReviewRepository reviewRepository;

    @Mock
    BookSwipeRepository swipeRepository;

    @Mock
    SearchHistoryRepository searchHistoryRepository;

    @Mock
    RecommendationExposureRepository exposureRepository;

    @Mock
    ReviewPermissionRepository reviewPermissionRepository;

    @Mock
    RecommendationAiPort aiPort;

    @Mock
    RecommendationQueuePort queuePort;

    @InjectMocks
    RecommendationService service;

    @BeforeEach
    void emptySignals() {
        lenient().when(recentRepository.findByMemberId("member")).thenReturn(List.of());
        lenient()
                .when(wishlistRepository.findByMemberIdAndBook_IsDeletedFalseOrderByCreatedAtDesc("member"))
                .thenReturn(List.of());
        lenient().when(reviewRepository.findByMemberId("member")).thenReturn(List.of());
        lenient().when(swipeRepository.findByMemberId("member")).thenReturn(List.of());
        lenient()
                .when(searchHistoryRepository.findByMemberIdOrderByCreatedAtDesc(eq("member"), any()))
                .thenReturn(List.of());
        lenient().when(reviewPermissionRepository.findByIdMemberId("member")).thenReturn(List.of());
        lenient().when(queuePort.get("member")).thenReturn(Optional.empty());
    }

    @Test
    void 규칙_점수와_AI_점수를_결합해_대기열을_생성한다() {
        Book book = book(1L, "클린 코드", 1000);
        when(searchHistoryRepository.findByMemberIdOrderByCreatedAtDesc(eq("member"), any()))
                .thenReturn(List.of(new SearchHistory("member", "리팩터링")));
        when(bookRepository.findBySaleStatusAndIsDeletedFalseOrderBySalesCountDescAverageRatingDesc(
                        eq(SaleStatus.ON_SALE), any()))
                .thenReturn(List.of(book));
        when(aiPort.rank(eq("member"), anyString(), anyInt()))
                .thenReturn(List.of(new RankedBook(1L, 0.9, "검색 근거에 맞는 추천이에요.")));

        var queue = service.getQueue("member", false);

        assertThat(queue.cards()).hasSize(1);
        assertThat(queue.cards().getFirst().recommendationReason()).contains("검색 근거");
        verify(exposureRepository).save(any());
        verify(queuePort).put(eq("member"), eq(queue), any());
    }

    private static Book book(Long id, String title, int salesCount) {
        Book book = Book.builder()
                .title(title)
                .author("저자")
                .publisher("출판사")
                .isbn("1234567890123")
                .category("IT")
                .price(10000)
                .description("설명")
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .salesCount(salesCount)
                .build();
        ReflectionTestUtils.setField(book, "bookId", id);
        return book;
    }
}
