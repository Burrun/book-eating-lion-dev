package com.bookeatinglion.isbn.client;

import com.bookeatinglion.isbn.dto.IsbnLookupResponse;

import java.util.Optional;

public interface BookOpenApiClient {

    Optional<IsbnLookupResponse> lookup(String isbn);
}
