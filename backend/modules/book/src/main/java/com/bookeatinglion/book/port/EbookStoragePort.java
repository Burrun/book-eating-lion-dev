package com.bookeatinglion.book.port;

import java.time.Duration;
import java.time.OffsetDateTime;

public interface EbookStoragePort {

    ReadUrl createReadUrl(String epubS3Key, Duration validity);

    record ReadUrl(String url, OffsetDateTime expiresAt) {}
}
