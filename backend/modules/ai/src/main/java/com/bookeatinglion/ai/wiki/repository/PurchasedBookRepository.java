package com.bookeatinglion.ai.wiki.repository;

import com.bookeatinglion.ai.wiki.domain.PurchasedBook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchasedBookRepository extends JpaRepository<PurchasedBook, PurchasedBook.Key> {

    @Query("select p.bookId from PurchasedBook p where p.memberId = :memberId order by p.bookId")
    List<Long> findBookIdsByMemberId(@Param("memberId") String memberId);

    long countByMemberId(String memberId);
}
