package com.bookeatinglion.isbn.client;

import com.bookeatinglion.isbn.dto.IsbnLookupResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
public class AladinBookApiClient implements BookOpenApiClient {

    private final RestClient restClient = RestClient.create("https://www.aladin.co.kr");
    private final String apiKey;

    public AladinBookApiClient(@Value("${app.aladin.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Optional<IsbnLookupResponse> lookup(String isbn) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            AladinItemLookupResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/ttb/api/ItemLookUp.aspx")
                            .queryParam("ttbkey", apiKey)
                            .queryParam("itemIdType", "ISBN13")
                            .queryParam("ItemId", isbn)
                            .queryParam("output", "js")
                            .queryParam("Version", "20131101")
                            .build())
                    .retrieve()
                    .body(AladinItemLookupResponse.class);

            if (response == null || response.item() == null || response.item().isEmpty()) {
                return Optional.empty();
            }
            Item item = response.item().get(0);
            return Optional.of(new IsbnLookupResponse(
                    isbn, item.title(), item.author(), item.publisher(), item.cover(), item.description()));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    private record AladinItemLookupResponse(List<Item> item) {
    }

    private record Item(String title, String author, String publisher, String cover, String description) {
    }
}
