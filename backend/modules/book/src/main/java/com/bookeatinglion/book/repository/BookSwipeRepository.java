package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.BookSwipe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookSwipeRepository extends JpaRepository<BookSwipe, Long> {

    Optional<BookSwipe> findByMemberIdAndBook_BookId(String memberId, Long bookId);

    List<BookSwipe> findByMemberId(String memberId);
}
