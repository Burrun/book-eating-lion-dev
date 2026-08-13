package com.bookeatinglion.book.port;

public interface RestockEmailSender {
    void send(RestockEmail email);

    record RestockEmail(
            String recipientEmail,
            String recipientName,
            String bookTitle,
            String author,
            String coverImageUrl,
            String bookDetailUrl) {}
}
