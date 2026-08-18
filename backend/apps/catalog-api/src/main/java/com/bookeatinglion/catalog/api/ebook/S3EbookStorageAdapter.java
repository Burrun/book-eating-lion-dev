package com.bookeatinglion.catalog.api.ebook;

import com.bookeatinglion.book.exception.EbookAccessUnavailableException;
import com.bookeatinglion.book.port.EbookStoragePort;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ebooks.storage", havingValue = "s3")
public class S3EbookStorageAdapter implements EbookStoragePort {

    private final S3Presigner presigner;

    @Value("${ebooks.s3.bucket-name}")
    private String bucketName;

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
            return new ReadUrl(presigned.url().toString(), OffsetDateTime.now().plus(validity));
        } catch (RuntimeException e) {
            throw new EbookAccessUnavailableException("eBook 열람 URL을 발급하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
