package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Wishlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByMemberIdAndBook_BookId(String memberId, Long bookId);

    boolean existsByMemberIdAndBook_BookIdAndBook_IsDeletedFalse(String memberId, Long bookId);

    List<Wishlist> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<Wishlist> findByMemberIdAndBook_IsDeletedFalseOrderByCreatedAtDesc(String memberId);

    void deleteByMemberIdAndBook_BookId(String memberId, Long bookId);
}
