package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Wishlist;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.WishlistRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final BookRepository bookRepository;

    @Transactional
    public void addWishlist(Long bookId, String memberId) {
        if (wishlistRepository.existsByMemberIdAndBook_BookIdAndBook_IsDeletedFalse(memberId, bookId)) {
            return;
        }
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        wishlistRepository.save(Wishlist.builder().memberId(memberId).book(book).build());
    }

    @Transactional
    public void removeWishlist(Long bookId, String memberId) {
        wishlistRepository.deleteByMemberIdAndBook_BookId(memberId, bookId);
    }

    public List<BookSummaryResponse> getMyWishlist(String memberId) {
        return wishlistRepository.findByMemberIdAndBook_IsDeletedFalseOrderByCreatedAtDesc(memberId).stream()
                .map(wishlist -> BookSummaryResponse.from(wishlist.getBook()))
                .toList();
    }
}
