package com.bookeatinglion.book.dto;

import java.time.OffsetDateTime;

/** uploadUrl로 PUT 업로드가 끝나면 epubS3Key를 도서 등록/수정 요청에 그대로 넘긴다. */
public record EpubUploadUrlResponse(String uploadUrl, String epubS3Key, OffsetDateTime expiresAt) {}
