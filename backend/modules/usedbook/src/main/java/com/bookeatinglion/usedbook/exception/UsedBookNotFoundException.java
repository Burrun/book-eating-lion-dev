package com.bookeatinglion.usedbook.exception;

public class UsedBookNotFoundException extends UsedBookException {

    public UsedBookNotFoundException(Long id) {
        super(UsedBookErrorCode.USED_BOOK_NOT_FOUND, "Used book not found: id=" + id);
    }
}
