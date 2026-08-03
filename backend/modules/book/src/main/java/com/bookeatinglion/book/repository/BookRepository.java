package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
}
