package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    Optional<ReadingProgress> findByMemberSubAndBook_BookId(String memberSub, Long bookId);
}
