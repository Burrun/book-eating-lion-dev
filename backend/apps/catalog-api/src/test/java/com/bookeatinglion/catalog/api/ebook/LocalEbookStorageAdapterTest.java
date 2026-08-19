package com.bookeatinglion.catalog.api.ebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalEbookStorageAdapterTest {

    private final LocalEbookStorageAdapter adapter = new LocalEbookStorageAdapter();

    @Test
    void 경로가_포함된_키에서_파일명을_추출한다() {
        var result = adapter.createReadUrl("ebooks/alice.epub", Duration.ofMinutes(10));

        assertThat(result.url()).isEqualTo("/ebooks/alice.epub");
    }

    @Test
    void 슬래시가_없는_키는_전체_문자열을_파일명으로_사용한다() {
        var result = adapter.createReadUrl("alice.epub", Duration.ofMinutes(10));

        assertThat(result.url()).isEqualTo("/ebooks/alice.epub");
    }

    @Test
    void 비어있거나_파일명이_없는_키는_거부한다() {
        assertThatThrownBy(() -> adapter.createReadUrl(null, Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.createReadUrl(" ", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.createReadUrl("ebooks/", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 로컬_모드는_업로드_URL_발급을_지원하지_않는다() {
        assertThatThrownBy(() -> adapter.createUploadUrl("alice.epub", Duration.ofMinutes(10)))
                .isInstanceOf(com.bookeatinglion.book.exception.EbookAccessUnavailableException.class);
    }
}
