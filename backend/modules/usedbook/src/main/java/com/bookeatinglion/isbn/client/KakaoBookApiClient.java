package com.bookeatinglion.isbn.client;

import com.bookeatinglion.isbn.dto.IsbnLookupResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
public class KakaoBookApiClient implements BookOpenApiClient {

    private final RestClient restClient = RestClient.create("https://dapi.kakao.com");
    private final String apiKey;

    public KakaoBookApiClient(@Value("${app.kakao.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Optional<IsbnLookupResponse> lookup(String isbn) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            KakaoBookSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v3/search/book")
                            .queryParam("target", "isbn")
                            .queryParam("query", isbn)
                            .build())
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(KakaoBookSearchResponse.class);

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                return Optional.empty();
            }
            Document document = response.documents().get(0);
            String author = (document.authors() == null || document.authors().isEmpty())
                    ? null
                    : String.join(", ", document.authors());
            return Optional.of(new IsbnLookupResponse(
                    isbn, document.title(), author, document.publisher(), document.thumbnail(), document.contents()));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    private record KakaoBookSearchResponse(List<Document> documents) {
    }

    private record Document(String title, List<String> authors, String publisher, String thumbnail, String contents) {
    }
}
