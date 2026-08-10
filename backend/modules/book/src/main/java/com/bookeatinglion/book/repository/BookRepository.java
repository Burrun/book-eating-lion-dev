package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findByIsDeletedFalse(Pageable pageable);

    Page<Book> findByCategoryAndIsDeletedFalse(String category, Pageable pageable);

    Optional<Book> findByBookIdAndIsDeletedFalse(Long bookId);

    Page<Book> findByIsDeleted(boolean isDeleted, Pageable pageable);

    boolean existsByCategoryAndIsDeletedFalse(String category);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndBookIdNot(String isbn, Long bookId);

    @Modifying(clearAutomatically = true)
    @Query("update Book b set b.category = :newCategory where b.category = :oldCategory")
    int renameCategory(@Param("oldCategory") String oldCategory, @Param("newCategory") String newCategory);

    @Query("select b from Book b where b.isDeleted = false and " +
           "(lower(b.title) like lower(concat('%', :q, '%')) " +
           "or lower(b.author) like lower(concat('%', :q, '%')))")
    Page<Book> search(@Param("q") String q, Pageable pageable);

    List<Book> findBySaleStatusAndIsDeletedFalseOrderBySalesCountDesc(SaleStatus saleStatus, Pageable pageable);

    List<Book> findBySaleStatusAndIsDeletedFalseOrderByPublishedDateDesc(SaleStatus saleStatus, Pageable pageable);
}
