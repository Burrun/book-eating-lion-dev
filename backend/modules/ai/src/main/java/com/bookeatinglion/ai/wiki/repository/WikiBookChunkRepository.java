package com.bookeatinglion.ai.wiki.repository;

import com.bookeatinglion.ai.wiki.domain.WikiBookChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiBookChunkRepository extends JpaRepository<WikiBookChunk, WikiBookChunk.Key> {

    List<WikiBookChunk> findByBookId(Long bookId);

    void deleteByBookId(Long bookId);
}
