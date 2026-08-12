package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.dto.AdminBookCreateRequest;
import com.bookeatinglion.book.dto.AdminBookResponse;
import com.bookeatinglion.book.dto.AdminBookUpdateRequest;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.CatalogConflictException;
import com.bookeatinglion.book.repository.BookRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
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
                .saleStatus(request.saleStatus())
                .publishedDate(request.publishedDate())
                .salesCount(0)
                .build();
        return AdminBookResponse.from(bookRepository.save(book));
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
                request.saleStatus() == null ? book.getSaleStatus() : request.saleStatus(),
                request.publishedDate() == null ? book.getPublishedDate() : request.publishedDate());
        return AdminBookResponse.from(book);
    }

    @Transactional
    public void delete(Long bookId) {
        Book book = findBook(bookId);
        if (!book.isDeleted()) {
            book.delete(LocalDateTime.now());
        }
    }

    private Book findBook(Long bookId) {
        return bookRepository.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
    }
}
