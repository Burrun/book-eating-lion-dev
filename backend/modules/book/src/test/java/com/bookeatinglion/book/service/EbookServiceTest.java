package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.port.EbookStoragePort;
import com.bookeatinglion.book.repository.BookRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EbookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EbookStoragePort ebookStoragePort;

    @InjectMocks
    private EbookService ebookService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ebookService, "readUrlValidity", Duration.ofMinutes(10));
    }

    @Test
    void ebook이_없는_도서는_미지원으로_응답한다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book(null)));

        EbookAccessResponse result = ebookService.getAccess(1L);

        assertThat(result.ebookAvailable()).isFalse();
        assertThat(result.presignedUrl()).isNull();
        verifyNoInteractions(ebookStoragePort);
    }

    @Test
    void 등록된_ebook의_열람_URL을_발급한다() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book("ebooks/alice.epub")));
        when(ebookStoragePort.createReadUrl("ebooks/alice.epub", Duration.ofMinutes(10)))
                .thenReturn(new EbookStoragePort.ReadUrl("https://signed.example/alice", expiresAt));

        EbookAccessResponse result = ebookService.getAccess(1L);

        assertThat(result.ebookAvailable()).isTrue();
        assertThat(result.presignedUrl()).isEqualTo("https://signed.example/alice");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    private Book book(String epubS3Key) {
        return Book.builder()
                .title("앨리스")
                .author("루이스 캐럴")
                .publisher("공개 도서")
                .isbn("9791100000001")
                .category("소설")
                .price(0)
                .epubS3Key(epubS3Key)
                .saleStatus(SaleStatus.ON_SALE)
                .salesCount(0)
                .build();
    }
}
