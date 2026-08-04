package com.bookeatinglion.usedbook.repository;

import com.bookeatinglion.usedbook.domain.UsedBook;
import com.bookeatinglion.usedbook.domain.UsedBookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsedBookRepository extends JpaRepository<UsedBook, Long> {

    @Query("select u from UsedBook u where " +
           "(:isbn is null or u.isbn = :isbn) and " +
           "(:status is null or u.status = :status) and " +
           "(:keyword is null or lower(u.title) like lower(concat('%', :keyword, '%')))")
    Page<UsedBook> search(@Param("isbn") String isbn,
                           @Param("status") UsedBookStatus status,
                           @Param("keyword") String keyword,
                           Pageable pageable);
}
