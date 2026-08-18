package com.bookeatinglion.catalog.api.ebook;

import com.bookeatinglion.book.port.EbookStoragePort;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 로컬 개발에서는 frontend/public/ebooks 아래 파일을 같은 응답 계약으로 제공한다. */
@Component
@ConditionalOnProperty(name = "ebooks.storage", havingValue = "local", matchIfMissing = true)
public class LocalEbookStorageAdapter implements EbookStoragePort {

    @Override
    public ReadUrl createReadUrl(String epubS3Key, Duration validity) {
        if (epubS3Key == null || epubS3Key.isBlank()) {
            throw new IllegalArgumentException("epubS3Key must not be blank");
        }

        int lastSlashIndex = epubS3Key.lastIndexOf('/');
        if (lastSlashIndex == epubS3Key.length() - 1) {
            throw new IllegalArgumentException("epubS3Key does not contain a file name: " + epubS3Key);
        }

        String fileName = lastSlashIndex < 0 ? epubS3Key : epubS3Key.substring(lastSlashIndex + 1);
        return new ReadUrl("/ebooks/" + fileName, OffsetDateTime.now().plus(validity));
    }
}
