package com.example.astrastudioopenai.service.knowledge;

import com.example.astrastudioopenai.service.knowledge.DocumentETLPipeline;
import com.example.astrastudioopenai.service.knowledge.RAGRetrievalService;
import com.example.astrastudioopenai.dto.response.RetrievedChunk;
import com.example.astrastudioopenai.entity.KnowledgeDocumentEntity;
import com.example.astrastudioopenai.repository.KnowledgeDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class KnowledgeService {

    @Autowired(required = false)
    private DocumentETLPipeline documentETLPipeline;

    @Autowired(required = false)
    private RAGRetrievalService ragRetrievalService;

    @Autowired(required = false)
    private KnowledgeDocumentRepository documentRepository;

    public ResponseEntity<Map<String, Object>> uploadDocument(String fileUrl, String fileName) {
        if (documentETLPipeline == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Knowledge base ETL pipeline not available",
                    "enabled", false));
        }
        try {
            String resolvedFileName = (fileName != null && !fileName.isBlank())
                    ? fileName
                    : fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            var doc = documentETLPipeline.processDocument(fileUrl, resolvedFileName);
            log.info("Document uploaded: id={}, status={}, chunks={}", doc.getId(), doc.getStatus(),
                    doc.getChunkCount());
            return ResponseEntity.ok(Map.of(
                    "documentId", doc.getId(),
                    "fileName", doc.getFilename(),
                    "status", doc.getStatus(),
                    "chunkCount", doc.getChunkCount() != null ? doc.getChunkCount() : 0));
        } catch (Exception e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage(),
                    "status", "FAILED"));
        }
    }

    public ResponseEntity<List<RetrievedChunk>> searchKnowledge(String query) {
        if (ragRetrievalService == null) {
            return ResponseEntity.status(503).body(List.of());
        }
        List<RetrievedChunk> results = ragRetrievalService.retrieve(query);
        log.info("Knowledge search: query='{}', results={}", query.length() > 50 ? query.substring(0, 50) : query,
                results.size());
        return ResponseEntity.ok(results);
    }

    public ResponseEntity<?> listDocuments(int page, int size) {
        if (documentRepository == null) {
            return ResponseEntity.status(503).body(List.of());
        }
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pageResult = documentRepository.findAll(pageable);
        return ResponseEntity.ok(Map.of(
                "content", pageResult.getContent(),
                "pageable", Map.of(
                        "pageNumber", pageResult.getNumber(),
                        "pageSize", pageResult.getSize(),
                        "sort", pageResult.getSort()),
                "last", pageResult.isLast(),
                "totalPages", pageResult.getTotalPages(),
                "totalElements", pageResult.getTotalElements(),
                "size", pageResult.getSize(),
                "number", pageResult.getNumber(),
                "first", pageResult.isFirst(),
                "numberOfElements", pageResult.getNumberOfElements(),
                "empty", pageResult.isEmpty()));
    }

    public ResponseEntity<Void> deleteDocument(Long id) {
        if (documentRepository == null) {
            return ResponseEntity.status(503).build();
        }
        if (!documentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            documentRepository.deleteById(id);
            log.info("Document deleted: id={}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete document: id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<Map<String, Object>> getDocumentStatus(Long id) {
        if (documentRepository == null) {
            return ResponseEntity.status(503).build();
        }
        return documentRepository.findById(id)
                .map(doc -> ResponseEntity.ok(Map.<String, Object>of(
                        "documentId", doc.getId(),
                        "fileName", doc.getFilename(),
                        "status", doc.getStatus(),
                        "chunkCount", doc.getChunkCount() != null ? doc.getChunkCount() : 0,
                        "errorMessage", doc.getErrorMessage() != null ? doc.getErrorMessage() : "")))
                .orElse(ResponseEntity.notFound().build());
    }

    public boolean isAvailable() {
        return documentETLPipeline != null && ragRetrievalService != null;
    }
}
