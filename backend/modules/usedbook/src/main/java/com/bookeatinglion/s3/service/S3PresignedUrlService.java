package com.bookeatinglion.s3.service;

import com.bookeatinglion.s3.dto.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private static final String KEY_PREFIX = "used-books";
    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(10);

    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket:book-eating-lion-local}")
    private String bucket;

    public PresignedUrlResponse generatePresignedUrl(String sellerId, String fileName) {
        String sanitizedFileName = sanitize(fileName);
        String key = KEY_PREFIX + "/" + sellerId + "/" + UUID.randomUUID() + "_" + sanitizedFileName;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(SIGNATURE_DURATION)
                .putObjectRequest(objectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        String fileUrl = "https://" + bucket + ".s3.amazonaws.com/" + key;
        return new PresignedUrlResponse(uploadUrl, fileUrl, key);
    }

    private String sanitize(String fileName) {
        String baseName = fileName.replace("\\", "/");
        int lastSlash = baseName.lastIndexOf('/');
        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }
        return baseName;
    }
}
