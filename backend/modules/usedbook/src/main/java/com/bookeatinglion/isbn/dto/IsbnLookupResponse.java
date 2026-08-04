package com.bookeatinglion.isbn.dto;

public record IsbnLookupResponse(
        String isbn,
        String title,
        String author,
        String publisher,
        String coverImageUrl,
        String description
) {
}
