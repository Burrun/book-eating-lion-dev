package com.bookeatinglion.s3.service;

import com.bookeatinglion.s3.dto.PresignedUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

class S3PresignedUrlServiceTest {

    private S3PresignedUrlService s3PresignedUrlService;

    @BeforeEach
    void setUp() {
        S3Presigner s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();
        s3PresignedUrlService = new S3PresignedUrlService(s3Presigner);
        ReflectionTestUtils.setField(s3PresignedUrlService, "bucket", "test-bucket");
    }

    @Test
    void presigned_url을_생성한다() {
        PresignedUrlResponse response = s3PresignedUrlService.generatePresignedUrl("seller-1", "cover.jpg");

        assertThat(response.uploadUrl()).contains("test-bucket");
        assertThat(response.key()).startsWith("used-books/seller-1/");
        assertThat(response.key()).endsWith("_cover.jpg");
        assertThat(response.fileUrl()).contains("test-bucket").contains(response.key());
    }

    @Test
    void 파일명에_경로가_포함되면_파일명만_사용한다() {
        PresignedUrlResponse response = s3PresignedUrlService.generatePresignedUrl("seller-1", "../../etc/passwd.jpg");

        assertThat(response.key()).doesNotContain("..");
        assertThat(response.key()).endsWith("_passwd.jpg");
    }

    @Test
    void 매번_고유한_키를_생성한다() {
        PresignedUrlResponse first = s3PresignedUrlService.generatePresignedUrl("seller-1", "cover.jpg");
        PresignedUrlResponse second = s3PresignedUrlService.generatePresignedUrl("seller-1", "cover.jpg");

        assertThat(first.key()).isNotEqualTo(second.key());
    }
}
