package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByBook_BookId(Long bookId, Pageable pageable);
}
