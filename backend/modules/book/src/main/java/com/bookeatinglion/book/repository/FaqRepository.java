package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Faq;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findByActiveTrueOrderBySortOrderAscFaqIdAsc();
    List<Faq> findByActiveTrueAndCategoryOrderBySortOrderAscFaqIdAsc(String category);
    List<Faq> findAllByOrderBySortOrderAscFaqIdAsc();
    List<Faq> findByCategoryOrderBySortOrderAscFaqIdAsc(String category);
}
