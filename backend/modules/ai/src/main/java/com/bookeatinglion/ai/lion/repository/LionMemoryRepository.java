package com.bookeatinglion.ai.lion.repository;

import com.bookeatinglion.ai.lion.domain.LionMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LionMemoryRepository extends JpaRepository<LionMemory, Long> {

    List<LionMemory> findByLionIdOrderByCreatedAtDesc(Long lionId);

    /**
     * RAG 의 R(Retrieval). 필터와 벡터 검색이 SQL 한 줄에 같은 트랜잭션으로 들어간다.
     *
     * 이게 pgvector 를 고른 이유다 — Valkey/Chroma/OpenSearch 는 벡터를 원본과 다른
     * 저장소에 두므로 "이 라이언의 기록 중 유사한 것" 같은 질의를 앱에서 후처리해야 한다.
     * 우리 벡터는 수천~수만 건이라 성능은 어느 쪽이든 충분했고, 기준은 정합성이었다.
     *
     * <=> 는 코사인 거리 연산자이며 HNSW 인덱스를 탄다(Phase 2-7 의 EXPLAIN 검증 대상).
     */
    @Query(
            value =
                    """
            SELECT * FROM lion_memories
            WHERE lion_id = :lionId AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """,
            nativeQuery = true)
    List<LionMemory> findSimilar(
            @Param("lionId") Long lionId, @Param("queryEmbedding") String queryEmbedding, @Param("topK") int topK);
}
