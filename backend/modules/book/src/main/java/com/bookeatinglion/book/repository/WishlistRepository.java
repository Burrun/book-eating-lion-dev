package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByMemberIdAndBook_BookId(Long memberId, Long bookId);

    List<Wishlist> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    void deleteByMemberIdAndBook_BookId(Long memberId, Long bookId);
}
