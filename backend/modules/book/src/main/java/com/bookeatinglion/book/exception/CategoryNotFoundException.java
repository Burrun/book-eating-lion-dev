package com.bookeatinglion.book.exception;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Long categoryId) {
        super("Category not found: id=" + categoryId);
    }
}
