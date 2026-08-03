package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.Wishlist;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final BookRepository bookRepository;

    @Transactional
    public void addWishlist(Long bookId, Long memberId) {
        if (wishlistRepository.findByMemberIdAndBookId(memberId, bookId).isPresent()) {
            return;
        }
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        wishlistRepository.save(Wishlist.builder().memberId(memberId).book(book).build());
    }

    @Transactional
    public void removeWishlist(Long bookId, Long memberId) {
        wishlistRepository.deleteByMemberIdAndBookId(memberId, bookId);
    }

    public List<BookSummaryResponse> getMyWishlist(Long memberId) {
        return wishlistRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(wishlist -> BookSummaryResponse.from(wishlist.getBook()))
                .toList();
    }
}
