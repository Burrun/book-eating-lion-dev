package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.RestockAlert;
import com.bookeatinglion.book.domain.RestockAlertStatus;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.port.MemberNotificationProfilePort;
import com.bookeatinglion.book.port.RestockEmailSender;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ProcessedRestockEventRepository;
import com.bookeatinglion.book.repository.RestockAlertRepository;
import com.bookeatinglion.common.event.InventoryRestockedEvent;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestockAlertServiceTest {
    @Mock
    RestockAlertRepository alertRepository;

    @Mock
    ProcessedRestockEventRepository processedEventRepository;

    @Mock
    BookRepository bookRepository;

    @Mock
    MemberNotificationProfilePort memberProfilePort;

    @Mock
    RestockEmailSender emailSender;

    @InjectMocks
    RestockAlertService service;

    @BeforeEach
    void setUp() throws Exception {
        setField(service, "maxRetries", 3);
        setField(service, "retryDelay", Duration.ofMinutes(5));
        setField(service, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void 재입고_신청을_생성한다() throws Exception {
        Book book = book();
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(alertRepository.findByMemberIdAndBookBookId("member-1", 1L)).thenReturn(Optional.empty());
        when(alertRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.subscribe(1L, "member-1");

        assertThat(result.status()).isEqualTo(RestockAlertStatus.WAITING);
    }

    @Test
    void 재입고_이벤트를_받으면_회원_이메일을_조회해_발송한다() throws Exception {
        RestockAlert alert = RestockAlert.builder()
                .book(book())
                .memberId("member-1")
                .requestedAt(LocalDateTime.now())
                .build();
        when(alertRepository.findByBookBookIdAndStatus(1L, RestockAlertStatus.WAITING))
                .thenReturn(List.of(alert));
        when(memberProfilePort.findByMemberId("member-1"))
                .thenReturn(new MemberNotificationProfilePort.NotificationProfile(
                        "member-1", "verified@example.com", "사자"));

        service.handleRestocked(InventoryRestockedEvent.occurred(1L, 0, 10));

        verify(emailSender).send(any());
        assertThat(alert.getStatus()).isEqualTo(RestockAlertStatus.SENT);
        verify(processedEventRepository).save(any());
    }

    @Test
    void 이메일_발송에_실패하면_재시도_정보를_저장한다() throws Exception {
        RestockAlert alert = RestockAlert.builder()
                .book(book())
                .memberId("member-1")
                .requestedAt(LocalDateTime.now())
                .build();
        when(alertRepository.findByBookBookIdAndStatus(1L, RestockAlertStatus.WAITING))
                .thenReturn(List.of(alert));
        when(memberProfilePort.findByMemberId("member-1")).thenThrow(new RuntimeException("member unavailable"));

        service.handleRestocked(InventoryRestockedEvent.occurred(1L, 0, 10));

        assertThat(alert.getStatus()).isEqualTo(RestockAlertStatus.FAILED);
        assertThat(alert.getRetryCount()).isEqualTo(1);
        assertThat(alert.getNextRetryAt()).isNotNull();
    }

    private Book book() throws Exception {
        Book book = Book.builder()
                .title("책")
                .author("저자")
                .publisher("출판사")
                .isbn("9781100000001")
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.OUT_OF_STOCK)
                .build();
        setField(book, "bookId", 1L);
        return book;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
