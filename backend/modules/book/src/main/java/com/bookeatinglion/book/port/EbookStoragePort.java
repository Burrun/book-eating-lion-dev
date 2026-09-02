package com.bookeatinglion.book.port;

import java.time.Duration;
import java.time.OffsetDateTime;

public interface EbookStoragePort {

    ReadUrl createReadUrl(String epubS3Key, Duration validity);

    /**
     * 관리자가 EPUB 원본을 올릴 presigned PUT URL을 발급한다. 반환된 key를 도서 등록/수정
     * 요청의 epubS3Key로 그대로 넘기면 된다 — 객체 키는 어댑터가 정한다(호출자가 키를 고르게
     * 하면 다른 책과 충돌하는 키를 만들 수 있다).
     */
    UploadUrl createUploadUrl(String fileName, Duration validity);

    record ReadUrl(String url, OffsetDateTime expiresAt) {}

    record UploadUrl(String url, String key, OffsetDateTime expiresAt) {}
}
