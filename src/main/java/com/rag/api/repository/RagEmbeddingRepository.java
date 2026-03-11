package com.rag.api.repository;

import com.rag.api.entity.RagEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RagEmbeddingRepository extends JpaRepository<RagEmbedding, UUID> {

    @Query(value = """
        SELECT file_name || ' -> ' || content
        FROM rag_embeddings
        ORDER BY embedding <-> CAST(:vector AS vector)
        LIMIT 7
        """, nativeQuery = true)
    List<String> searchSimilar(@Param("vector") String vector);
}