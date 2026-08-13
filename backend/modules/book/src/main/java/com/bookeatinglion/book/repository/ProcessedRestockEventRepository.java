package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.ProcessedRestockEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedRestockEventRepository extends JpaRepository<ProcessedRestockEvent, String> {}
