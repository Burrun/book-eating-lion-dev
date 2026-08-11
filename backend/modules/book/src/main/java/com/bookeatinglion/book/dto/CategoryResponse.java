package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.Category;

public record CategoryResponse(Long categoryId, String categoryName, Long parentId, int sortOrder, boolean active) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getCategoryId(),
                category.getCategoryName(),
                category.getParent() == null ? null : category.getParent().getCategoryId(),
                category.getSortOrder(),
                category.isActive());
    }
}
