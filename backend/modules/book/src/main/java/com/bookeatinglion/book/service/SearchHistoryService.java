package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.SearchHistory;
import com.bookeatinglion.book.repository.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;

    @Transactional
    public void record(String memberId, String query) {
        if (memberId == null || query == null || query.isBlank()) {
            return;
        }
        searchHistoryRepository.save(new SearchHistory(memberId, query.trim()));
    }
}
