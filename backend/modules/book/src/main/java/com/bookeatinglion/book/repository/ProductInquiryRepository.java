package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.InquiryStatus;
import com.bookeatinglion.book.domain.ProductInquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {

    @Query(
            """
            select i from ProductInquiry i
            where i.book.bookId = :bookId
              and i.deleted = false
              and (i.privateInquiry = false or i.memberId = :memberId)
            order by i.createdAt desc, i.inquiryId desc
            """)
    Page<ProductInquiry> findVisibleByBookId(
            @Param("bookId") Long bookId, @Param("memberId") String memberId, Pageable pageable);

    @Query(
            """
            select i from ProductInquiry i
            where (:bookId is null or i.book.bookId = :bookId)
              and (:status is null or i.status = :status)
            order by i.createdAt desc, i.inquiryId desc
            """)
    Page<ProductInquiry> findForAdmin(
            @Param("bookId") Long bookId, @Param("status") InquiryStatus status, Pageable pageable);
}
