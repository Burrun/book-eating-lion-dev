package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderBySortOrderAscCategoryNameAsc();

    List<Category> findAllByOrderBySortOrderAscCategoryNameAsc();

    boolean existsByCategoryName(String categoryName);

    boolean existsByCategoryNameAndCategoryIdNot(String categoryName, Long categoryId);

    boolean existsByParent_CategoryIdAndActiveTrue(Long parentId);

    Optional<Category> findByCategoryNameAndActiveTrue(String categoryName);
}
