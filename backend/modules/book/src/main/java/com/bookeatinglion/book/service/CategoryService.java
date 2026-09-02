package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Category;
import com.bookeatinglion.book.dto.CategoryCreateRequest;
import com.bookeatinglion.book.dto.CategoryResponse;
import com.bookeatinglion.book.dto.CategoryUpdateRequest;
import com.bookeatinglion.book.exception.CatalogConflictException;
import com.bookeatinglion.book.exception.CategoryNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAscCategoryNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscCategoryNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse getCategory(Long categoryId) {
        return CategoryResponse.from(findCategory(categoryId));
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsByCategoryName(request.categoryName())) {
            throw new CatalogConflictException("Category name already exists: " + request.categoryName());
        }
        Category parent = findOptionalParent(request.parentId());
        return CategoryResponse.from(
                categoryRepository.save(new Category(request.categoryName(), parent, request.sortOrder())));
    }

    @Transactional
    public CategoryResponse update(Long categoryId, CategoryUpdateRequest request) {
        Category category = findCategory(categoryId);
        if (categoryRepository.existsByCategoryNameAndCategoryIdNot(request.categoryName(), categoryId)) {
            throw new CatalogConflictException("Category name already exists: " + request.categoryName());
        }
        Category parent = findOptionalParent(request.parentId());
        ensureNoCycle(categoryId, parent);
        String oldName = category.getCategoryName();
        category.update(request.categoryName(), parent, request.sortOrder());
        if (!oldName.equals(request.categoryName())) {
            bookRepository.renameCategory(oldName, request.categoryName());
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deactivate(Long categoryId) {
        Category category = findCategory(categoryId);
        if (bookRepository.existsByCategoryAndIsDeletedFalse(category.getCategoryName())) {
            throw new CatalogConflictException("Category has active books: id=" + categoryId);
        }
        if (categoryRepository.existsByParent_CategoryIdAndActiveTrue(categoryId)) {
            throw new CatalogConflictException("Category has active child categories: id=" + categoryId);
        }
        category.deactivate();
    }

    public String getActiveCategoryName(String categoryName) {
        return categoryRepository
                .findByCategoryNameAndActiveTrue(categoryName)
                .orElseThrow(() -> new CatalogConflictException("Active category not found: " + categoryName))
                .getCategoryName();
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Category findOptionalParent(Long parentId) {
        return parentId == null ? null : findCategory(parentId);
    }

    private void ensureNoCycle(Long categoryId, Category parent) {
        Category current = parent;
        while (current != null) {
            if (categoryId.equals(current.getCategoryId())) {
                throw new CatalogConflictException("Category hierarchy cannot contain a cycle");
            }
            current = current.getParent();
        }
    }
}
