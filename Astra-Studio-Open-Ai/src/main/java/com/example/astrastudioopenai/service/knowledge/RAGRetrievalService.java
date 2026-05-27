package com.example.astrastudioopenai.service.knowledge;

import com.example.astrastudioopenai.common.utils.FileTypes;
import com.example.astrastudioopenai.entity.DocumentChunkEntity;
import com.example.astrastudioopenai.repository.DocumentChunkRepository;
import com.example.astrastudioopenai.dto.response.RetrievedChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class RAGRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RAGRetrievalService.class);

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepo;
    private final MultimodalEmbeddingService multimodalEmbeddingService;

    @Value("${knowledge-base.rag.top-k:5}")
    private int topK;

    @Value("${knowledge-base.rag.similarity-threshold:0.5}")
    private double similarityThreshold;

    @Value("${knowledge-base.rag.retrieval-timeout-ms:3000}")
    private long retrievalTimeoutMs;

    @Value("${knowledge-base.rag.cache-ttl-seconds:30}")
    private long cacheTtlSeconds;

    private final ConcurrentHashMap<String, CachedResult> resultCache = new ConcurrentHashMap<>();

    public RAGRetrievalService(EmbeddingModel embeddingModel, DocumentChunkRepository chunkRepo,
            MultimodalEmbeddingService multimodalEmbeddingService) {
        this.embeddingModel = embeddingModel;
        this.chunkRepo = chunkRepo;
        this.multimodalEmbeddingService = multimodalEmbeddingService;
    }

    public List<RetrievedChunk> retrieve(String query) {
        String cacheKey = normalizeKey(query);

        CachedResult cached = resultCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("RAG cache hit for query='{}'", truncate(query, 50));
            return cached.results();
        }

        try {
            CompletableFuture<List<RetrievedChunk>> future = CompletableFuture.supplyAsync(() -> doRetrieve(query));
            List<RetrievedChunk> results = future.get(retrievalTimeoutMs, TimeUnit.MILLISECONDS);
            resultCache.put(cacheKey, new CachedResult(results, Instant.now().plusSeconds(cacheTtlSeconds)));
            return results;
        } catch (TimeoutException e) {
            log.warn("RAG retrieval timed out after {}ms", retrievalTimeoutMs);
            return List.of();
        } catch (Exception e) {
            log.error("RAG retrieval failed", e);
            return List.of();
        }
    }

    private List<RetrievedChunk> doRetrieve(String query) {
        Instant start = Instant.now();

        List<DocumentChunkEntity> allResults = new java.util.ArrayList<>();

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        float[] textVec = queryEmbedding.vector();
        String textVecStr = formatVector(textVec);

        log.info("RAG query='{}' | text_vec_dim={} | vec_preview=[{},{}...]",
                truncate(query, 50), textVec.length,
                textVec.length > 0 ? String.format("%.6f", textVec[0]) : "N/A",
                textVec.length > 1 ? String.format("%.6f", textVec[1]) : "N/A");

        double maxDist = 1.0 - similarityThreshold;

        List<DocumentChunkEntity> textResults = chunkRepo.findSimilarChunksByContentType("text", textVecStr, maxDist,
                topK);
        log.info("RAG text_path: maxDist={}, results={}", maxDist, textResults.size());

        if (textResults.isEmpty()) {
            List<DocumentChunkEntity> fallbackText = chunkRepo.findSimilarChunksByContentType("text", textVecStr, 1.0,
                    topK);
            if (!fallbackText.isEmpty()) {
                log.warn("RAG text_path: no results at threshold {}, fallback: {} chunks", similarityThreshold,
                        fallbackText.size());
                textResults = fallbackText;
            }
        }
        allResults.addAll(textResults);

        if (multimodalEmbeddingService.isEnabled()) {
            try {
                float[] multimodalVec = multimodalEmbeddingService.embedText(query);
                String multimodalVecStr = formatVector(multimodalVec);

                log.info("RAG multimodal_path: vec_dim={}, vec_preview=[{},{}...]",
                        multimodalVec.length,
                        multimodalVec.length > 0 ? String.format("%.6f", multimodalVec[0]) : "N/A",
                        multimodalVec.length > 1 ? String.format("%.6f", multimodalVec[1]) : "N/A");

                List<DocumentChunkEntity> imageResults = chunkRepo.findSimilarChunksByContentType("image",
                        multimodalVecStr, maxDist, topK);
                log.info("RAG image_path: maxDist={}, results={}", maxDist, imageResults.size());

                if (imageResults.isEmpty()) {
                    List<DocumentChunkEntity> fallbackImage = chunkRepo.findSimilarChunksByContentType("image",
                            multimodalVecStr, 1.0, topK);
                    if (!fallbackImage.isEmpty()) {
                        log.warn("RAG image_path: no results at threshold {}, fallback: {} chunks", similarityThreshold,
                                fallbackImage.size());
                        imageResults = fallbackImage;
                    }
                }
                allResults.addAll(imageResults);
            } catch (Exception e) {
                log.warn("RAG multimodal_path failed, skipping image retrieval: {}", e.getMessage());
            }
        }

        if (log.isDebugEnabled()) {
            long totalChunks = chunkRepo.count();
            long nullCount = chunkRepo.countNullEmbeddings();
            long nonNullCount = chunkRepo.countNonNullEmbeddings();
            log.debug("RAG db_stats: total={}, non_null_embeddings={}, null_embeddings={}",
                    totalChunks, nonNullCount, nullCount);
        }

        List<RetrievedChunk> results = allResults.stream()
                .filter(chunk -> chunk.getContent() != null)
                .limit(topK)
                .map(entity -> {
                    RetrievedChunk rc = new RetrievedChunk();
                    rc.setChunkId((long) entity.getChunkIndex());
                    rc.setContentSnippet(truncateContent(entity.getContent()));
                    String docName = entity.getDocument() != null ? entity.getDocument().getFilename() : "unknown";
                    rc.setDocumentName(docName);
                    rc.setContent(entity.getContent());
                    boolean isImage = FileTypes.isImageFile(docName);
                    rc.setSourceType(isImage ? "image" : "text");
                    rc.setMetadata(entity.getMetadataJson());
                    return rc;
                })
                .collect(Collectors.toList());

        log.info("RAG retrieved {} chunks in {}ms for query='{}' (text={}, image={})",
                results.size(), Duration.between(start, Instant.now()).toMillis(),
                truncate(query, 50),
                textResults.size(),
                allResults.size() - textResults.size());
        return results;
    }

    public String formatContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder("\n\n--- 知识库参考 ---\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            String docName = c.getDocumentName();
            boolean isImage = FileTypes.isImageFile(docName);

            if (isImage) {
                sb.append(String.format("[%d] %s\n\n", i + 1, c.getContent()));
            } else {
                sb.append(String.format("[%d] 文档: %s\n%s\n\n", i + 1, docName, c.getContent()));
            }
        }
        sb.append("--- 参考结束 ---");
        return sb.toString();
    }

    private static String normalizeKey(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private static String formatVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(v[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String truncateContent(String content) {
        if (content == null)
            return "";
        int maxLen = 100;
        return content.length() > maxLen ? content.substring(0, maxLen) + "..." : content;
    }

    record CachedResult(List<RetrievedChunk> results, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
