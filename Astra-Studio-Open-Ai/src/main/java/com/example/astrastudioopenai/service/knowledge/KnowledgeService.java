package com.example.astrastudioopenai.service.knowledge;

import com.example.astrastudioopenai.common.utils.FileTypes;
import com.example.astrastudioopenai.service.knowledge.DocumentETLPipeline;
import com.example.astrastudioopenai.service.knowledge.RAGRetrievalService;
import com.example.astrastudioopenai.dto.response.DocumentPageResponse;
import com.example.astrastudioopenai.dto.response.RetrievedChunk;
import com.example.astrastudioopenai.entity.KnowledgeDocumentEntity;
import com.example.astrastudioopenai.repository.KnowledgeDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${knowledge-base.multimodal-embedding.enabled:true}")
    private boolean multimodalEmbeddingEnabled;

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

            boolean isImage = isImageFile(resolvedFileName);

            if (isImage && !multimodalEmbeddingEnabled) {
                log.warn("图片上传被拒绝: 功能已禁用, file={}", resolvedFileName);
                return ResponseEntity.status(403).body(Map.of(
                        "error", "图片上传功能已禁用",
                        "status", "REJECTED"));
            }

            var doc = documentETLPipeline.processDocument(fileUrl, resolvedFileName);
            log.info("Document uploaded: id={}, status={}, chunks={}, contentType={}",
                    doc.getId(), doc.getStatus(), doc.getChunkCount(), doc.getContentType());

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("documentId", doc.getId());
            response.put("fileName", doc.getFilename());
            response.put("status", doc.getStatus());
            response.put("chunkCount", doc.getChunkCount() != null ? doc.getChunkCount() : 0);
            response.put("contentType", doc.getContentType() != null ? doc.getContentType() : "text");

            return ResponseEntity.ok(response);
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

    public ResponseEntity<DocumentPageResponse> listDocuments(int page, int size) {
        if (documentRepository == null) {
            return ResponseEntity.status(503).body(DocumentPageResponse.builder()
                    .content(List.of())
                    .pageable(DocumentPageResponse.PageableInfo.builder().build())
                    .empty(true).build());
        }
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pageResult = documentRepository.findAll(pageable);
        return ResponseEntity.ok(DocumentPageResponse.builder()
                .content(pageResult.getContent())
                .pageable(DocumentPageResponse.PageableInfo.builder()
                        .pageNumber(pageResult.getNumber())
                        .pageSize(pageResult.getSize())
                        .sort(pageResult.getSort().toString())
                        .build())
                .last(pageResult.isLast())
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .size(pageResult.getSize())
                .number(pageResult.getNumber())
                .first(pageResult.isFirst())
                .numberOfElements(pageResult.getNumberOfElements())
                .empty(pageResult.isEmpty())
                .build());
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

    private boolean isImageFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
               lowerName.endsWith(".png") || lowerName.endsWith(".webp");
    }
}
