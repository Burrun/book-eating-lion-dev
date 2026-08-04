package com.bookeatinglion.usedbook.exception;

public class S3UploadException extends UsedBookException {

    public S3UploadException(String message, Throwable cause) {
        super(UsedBookErrorCode.S3_UPLOAD_ERROR, message, cause);
    }
}
