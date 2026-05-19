package com.example.astrastudioopenai.repository;

import com.example.astrastudioopenai.entity.DocumentChunkEntity;
import com.example.astrastudioopenai.entity.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {

    List<DocumentChunkEntity> findByDocumentOrderByChunkIndexAsc(KnowledgeDocumentEntity document);

    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    int countByDocument(KnowledgeDocumentEntity document);

    @Query(value = "SELECT * FROM document_chunks WHERE (:docId IS NULL OR document_id = :docId) AND (embedding <=> CAST(:queryVec AS vector)) <= :maxDist ORDER BY embedding <=> CAST(:queryVec AS vector) LIMIT :topK", nativeQuery = true)
    List<DocumentChunkEntity> findSimilarChunks(@Param("docId") Long docId, @Param("queryVec") String queryVec, @Param("maxDist") double maxDist, @Param("topK") int topK);

    @Query(value = "SELECT count(*) FROM document_chunks WHERE embedding IS NULL", nativeQuery = true)
    long countNullEmbeddings();

    @Query(value = "SELECT count(*) FROM document_chunks WHERE embedding IS NOT NULL", nativeQuery = true)
    long countNonNullEmbeddings();
}
