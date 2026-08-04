package com.bookeatinglion.usedbook.service;

import com.bookeatinglion.usedbook.domain.UsedBook;
import com.bookeatinglion.usedbook.domain.UsedBookStatus;
import com.bookeatinglion.usedbook.dto.UsedBookCreateRequest;
import com.bookeatinglion.usedbook.dto.UsedBookResponse;
import com.bookeatinglion.usedbook.dto.UsedBookSummaryResponse;
import com.bookeatinglion.usedbook.exception.UsedBookNotFoundException;
import com.bookeatinglion.usedbook.repository.UsedBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsedBookService {

    private final UsedBookRepository usedBookRepository;

    @Transactional
    public UsedBookResponse createUsedBook(String sellerId, UsedBookCreateRequest request) {
        UsedBook usedBook = UsedBook.builder()
                .sellerId(sellerId)
                .isbn(request.isbn())
                .title(request.title())
                .author(request.author())
                .publisher(request.publisher())
                .coverImageUrl(request.coverImageUrl())
                .price(request.price())
                .condition(request.condition())
                .description(request.description())
                .imageUrls(request.imageUrls())
                .build();
        return UsedBookResponse.from(usedBookRepository.save(usedBook));
    }

    public Page<UsedBookSummaryResponse> getUsedBooks(String isbn, UsedBookStatus status, String keyword, Pageable pageable) {
        return usedBookRepository.search(isbn, status, keyword, pageable).map(UsedBookSummaryResponse::from);
    }

    public UsedBookResponse getUsedBook(Long id) {
        UsedBook usedBook = usedBookRepository.findById(id)
                .orElseThrow(() -> new UsedBookNotFoundException(id));
        return UsedBookResponse.from(usedBook);
    }
}
