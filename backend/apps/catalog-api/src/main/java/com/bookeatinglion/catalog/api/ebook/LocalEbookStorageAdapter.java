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
        String fileName = epubS3Key.substring(epubS3Key.lastIndexOf('/') + 1);
        return new ReadUrl("/ebooks/" + fileName, OffsetDateTime.now().plus(validity));
    }
}
