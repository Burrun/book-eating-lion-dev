package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.AdminBookCreateRequest;
import com.bookeatinglion.book.dto.AdminBookResponse;
import com.bookeatinglion.book.dto.AdminBookUpdateRequest;
import com.bookeatinglion.book.event.BookRecommendationIndexEvent;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.CatalogConflictException;
import com.bookeatinglion.book.repository.BookRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBookService {

    private final BookRepository bookRepository;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher eventPublisher;

    public Page<AdminBookResponse> getBooks(boolean includeDeleted, Pageable pageable) {
        Page<Book> books =
                includeDeleted ? bookRepository.findAll(pageable) : bookRepository.findByIsDeleted(false, pageable);
        return books.map(AdminBookResponse::from);
    }

    public AdminBookResponse getBook(Long bookId) {
        return AdminBookResponse.from(findBook(bookId));
    }

    @Transactional
    public AdminBookResponse create(AdminBookCreateRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new CatalogConflictException("ISBN already exists: " + request.isbn());
        }
        String category = categoryService.getActiveCategoryName(request.category());
        Book book = Book.builder()
                .title(request.title())
                .author(request.author())
                .publisher(request.publisher())
                .isbn(request.isbn())
                .category(category)
                .price(request.price())
                .coverImageUrl(request.coverImageUrl())
                .description(request.description())
                .detailedSynopsis(request.detailedSynopsis())
                .epubS3Key(request.epubS3Key())
                .saleStatus(request.saleStatus())
                .publishedDate(request.publishedDate())
                .salesCount(0)
                .build();
        Book saved = bookRepository.save(book);
        publishIndexUpdate(saved);
        return AdminBookResponse.from(saved);
    }

    @Transactional
    public AdminBookResponse update(Long bookId, AdminBookUpdateRequest request) {
        Book book = findBook(bookId);
        String isbn = request.isbn() == null ? book.getIsbn() : request.isbn();
        if (!isbn.equals(book.getIsbn()) && bookRepository.existsByIsbnAndBookIdNot(isbn, bookId)) {
            throw new CatalogConflictException("ISBN already exists: " + isbn);
        }
        String category = request.category() == null
                ? book.getCategory()
                : categoryService.getActiveCategoryName(request.category());
        book.update(
                request.title() == null ? book.getTitle() : request.title(),
                request.author() == null ? book.getAuthor() : request.author(),
                request.publisher() == null ? book.getPublisher() : request.publisher(),
                isbn,
                category,
                request.price() == null ? book.getPrice() : request.price(),
                request.coverImageUrl() == null ? book.getCoverImageUrl() : request.coverImageUrl(),
                request.description() == null ? book.getDescription() : request.description(),
                request.detailedSynopsis() == null ? book.getDetailedSynopsis() : request.detailedSynopsis(),
                request.epubS3Key() == null ? book.getEpubS3Key() : request.epubS3Key(),
                request.saleStatus() == null ? book.getSaleStatus() : request.saleStatus(),
                request.publishedDate() == null ? book.getPublishedDate() : request.publishedDate());
        publishIndexUpdate(book);
        return AdminBookResponse.from(book);
    }

    @Transactional
    public void delete(Long bookId) {
        Book book = findBook(bookId);
        if (!book.isDeleted()) {
            book.delete(LocalDateTime.now());
            eventPublisher.publishEvent(BookRecommendationIndexEvent.delete(bookId));
        }
    }

    /** 기존 활성 도서를 추천 벡터 인덱스에 최초 적재하거나 전체 재구축할 때 사용한다. */
    @Transactional
    public int reindexRecommendations() {
        List<Book> books = bookRepository.findBySaleStatusAndIsDeletedFalse(SaleStatus.ON_SALE);
        books.forEach(book -> eventPublisher.publishEvent(BookRecommendationIndexEvent.upsert(book)));
        return books.size();
    }

    private Book findBook(Long bookId) {
        return bookRepository.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
    }

    private void publishIndexUpdate(Book book) {
        // JPA 운영 경로에서는 ID가 항상 있지만, 영속화 어댑터 교체/단위 테스트에도 안전하게 둔다.
        if (book.getBookId() != null) {
            BookRecommendationIndexEvent event = !book.isDeleted() && book.getSaleStatus() == SaleStatus.ON_SALE
                    ? BookRecommendationIndexEvent.upsert(book)
                    : BookRecommendationIndexEvent.delete(book.getBookId());
            eventPublisher.publishEvent(event);
        }
    }
}
