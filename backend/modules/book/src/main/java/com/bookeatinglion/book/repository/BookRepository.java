package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    Page<Book> findByIsDeletedFalse(Pageable pageable);

    Page<Book> findByCategoryAndIsDeletedFalse(String category, Pageable pageable);

    Page<Book> findByEpubS3KeyIsNotNullAndIsDeletedFalse(Pageable pageable);

    // 아래 SaleStatusNot 3종은 고객용 목록 전용이다. 판매중지 도서를 페이지네이션
    // 이전에 걸러야 하므로 쿼리에서 뺀다 - 조회 후 자바에서 필터하면 20개를 받아
    // 19개만 보여주게 되고 totalElements 도 틀어진다.
    // 관리자 목록(/api/catalog/admin/books)은 AdminBookService 가 따로 처리하므로
    // 판매중지 도서가 그대로 보인다.
    Page<Book> findBySaleStatusNotAndIsDeletedFalse(SaleStatus saleStatus, Pageable pageable);

    Page<Book> findByCategoryAndSaleStatusNotAndIsDeletedFalse(
            String category, SaleStatus saleStatus, Pageable pageable);

    Page<Book> findByEpubS3KeyIsNotNullAndSaleStatusNotAndIsDeletedFalse(
            SaleStatus saleStatus, Pageable pageable);

    Optional<Book> findByBookIdAndIsDeletedFalse(Long bookId);

    boolean existsByBookIdAndIsDeletedFalse(Long bookId);

    Page<Book> findByIsDeleted(boolean isDeleted, Pageable pageable);

    boolean existsByCategoryAndIsDeletedFalse(String category);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndBookIdNot(String isbn, Long bookId);

    @Modifying(clearAutomatically = true)
    @Query("update Book b set b.category = :newCategory where b.category = :oldCategory")
    int renameCategory(@Param("oldCategory") String oldCategory, @Param("newCategory") String newCategory);

    @Query("select b from Book b where b.isDeleted = false and b.saleStatus <> :excluded and "
            + "(lower(b.title) like lower(concat('%', :q, '%')) "
            + "or lower(b.author) like lower(concat('%', :q, '%')))")
    Page<Book> search(@Param("q") String q, @Param("excluded") SaleStatus excluded, Pageable pageable);

    List<Book> findBySaleStatusAndIsDeletedFalseOrderBySalesCountDesc(SaleStatus saleStatus, Pageable pageable);

    List<Book> findBySaleStatusAndIsDeletedFalseOrderByPublishedDateDesc(SaleStatus saleStatus, Pageable pageable);

    List<Book> findBySaleStatusAndIsDeletedFalseOrderBySalesCountDescAverageRatingDesc(
            SaleStatus saleStatus, Pageable pageable);

    List<Book> findBySaleStatusAndIsDeletedFalse(SaleStatus saleStatus);
}
