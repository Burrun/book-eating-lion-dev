package com.bookeatinglion.ai.api.client;

import com.bookeatinglion.ai.api.config.AiProperties;
import com.bookeatinglion.ai.wiki.port.ObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * EPUB 원본을 S3 에서 받아온다.
 *
 * <p>버킷은 설정에서 고정한다 — 호출자가 버킷을 고르게 하면 메시지 하나로 남의 버킷을
 * 읽는 경로가 생긴다. 벡터 버킷({@code app.ai.vector.bucket-name})과는 다른 버킷이다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.clients", havingValue = "bedrock", matchIfMissing = true)
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3;
    private final String bucketName;

    public S3ObjectStorageAdapter(S3Client s3, AiProperties props) {
        this.s3 = s3;
        this.bucketName = props.ingest().bucketName();
    }

    @Override
    public byte[] download(String key) {
        try {
            ResponseBytes<?> object = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucketName).key(key).build());
            return object.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("EPUB 이 없다: s3://%s/%s".formatted(bucketName, key), e);
        } catch (S3Exception e) {
            throw new IllegalStateException("EPUB 을 읽지 못했다: s3://%s/%s".formatted(bucketName, key), e);
        }
    }
}
