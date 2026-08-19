package com.bookeatinglion.catalog.api.ebook;

import com.bookeatinglion.book.exception.EbookAccessUnavailableException;
import com.bookeatinglion.book.port.EbookStoragePort;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@Slf4j
@ConditionalOnProperty(name = "ebooks.storage", havingValue = "s3")
public class S3EbookStorageAdapter implements EbookStoragePort {

    private final S3Presigner presigner;
    private final String bucketName;

    public S3EbookStorageAdapter(S3Presigner presigner, @Value("${ebooks.s3.bucket-name:}") String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("EBOOK_S3_BUCKET must be configured when EBOOK_STORAGE=s3");
        }
        this.presigner = presigner;
        this.bucketName = bucketName;
    }

    @Override
    public ReadUrl createReadUrl(String epubS3Key, Duration validity) {
        try {
            GetObjectRequest getObject = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(epubS3Key)
                    .responseContentType("application/epub+zip")
                    .build();
            var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(validity)
                    .getObjectRequest(getObject)
                    .build());
            OffsetDateTime expiresAt = OffsetDateTime.ofInstant(presigned.expiration(), ZoneOffset.UTC);
            return new ReadUrl(presigned.url().toString(), expiresAt);
        } catch (RuntimeException e) {
            log.error("eBook Presigned URL 발급 실패. bucket={}, key={}", bucketName, epubS3Key, e);
            throw new EbookAccessUnavailableException("eBook 열람 URL을 발급하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    /** 키는 호출자가 아니라 여기서 정한다 — 관리자가 키를 고르게 하면 다른 책과 충돌할 수 있다. */
    @Override
    public UploadUrl createUploadUrl(String fileName, Duration validity) {
        String key = "epubs/" + UUID.randomUUID() + "_" + sanitizeFileName(fileName);
        try {
            PutObjectRequest putObject = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/epub+zip")
                    .build();
            var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(validity)
                    .putObjectRequest(putObject)
                    .build());
            OffsetDateTime expiresAt = OffsetDateTime.ofInstant(presigned.expiration(), ZoneOffset.UTC);
            return new UploadUrl(presigned.url().toString(), key, expiresAt);
        } catch (RuntimeException e) {
            log.error("eBook 업로드 Presigned URL 발급 실패. bucket={}, key={}", bucketName, key, e);
            throw new EbookAccessUnavailableException("eBook 업로드 URL을 발급하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private static String sanitizeFileName(String fileName) {
        String baseName = fileName.replace("\\", "/");
        int lastSlash = baseName.lastIndexOf('/');
        return lastSlash >= 0 ? baseName.substring(lastSlash + 1) : baseName;
    }
}
