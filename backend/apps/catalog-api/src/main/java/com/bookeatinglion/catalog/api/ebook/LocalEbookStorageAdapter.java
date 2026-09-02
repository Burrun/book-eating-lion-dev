package com.bookeatinglion.catalog.api.ebook;

import com.bookeatinglion.book.exception.EbookAccessUnavailableException;
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

    /** 로컬 모드는 frontend/public/ebooks 의 정적 파일만 읽을 수 있다 — 실제 업로드 대상이 없다. */
    @Override
    public UploadUrl createUploadUrl(String fileName, Duration validity) {
        throw new EbookAccessUnavailableException("EPUB 업로드는 S3 저장소 모드(EBOOK_STORAGE=s3)에서만 지원됩니다. 로컬 모드에서는 "
                + "frontend/public/ebooks 에 파일을 직접 두고 epubS3Key를 수동으로 입력하세요.");
    }
}
