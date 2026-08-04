package com.bookeatinglion.usedbook.exception;

public class UsedBookNotFoundException extends RuntimeException {

    public UsedBookNotFoundException(Long id) {
        super("Used book not found: id=" + id);
    }
}
