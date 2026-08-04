package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findByCategory(String category, Pageable pageable);

    @Query("select b from Book b where lower(b.title) like lower(concat('%', :q, '%')) " +
           "or lower(b.author) like lower(concat('%', :q, '%'))")
    Page<Book> search(@Param("q") String q, Pageable pageable);

    List<Book> findBySaleStatusOrderBySalesCountDesc(SaleStatus saleStatus, Pageable pageable);

    List<Book> findBySaleStatusOrderByPublishedDateDesc(SaleStatus saleStatus, Pageable pageable);
}
