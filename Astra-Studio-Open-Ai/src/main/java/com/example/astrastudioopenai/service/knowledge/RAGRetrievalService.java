package com.example.astrastudioopenai.service.knowledge;

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

    @Value("${knowledge-base.rag.top-k:5}")
    private int topK;

    @Value("${knowledge-base.rag.similarity-threshold:0.5}")
    private double similarityThreshold;

    @Value("${knowledge-base.rag.retrieval-timeout-ms:3000}")
    private long retrievalTimeoutMs;

    @Value("${knowledge-base.rag.cache-ttl-seconds:30}")
    private long cacheTtlSeconds;

    private final ConcurrentHashMap<String, CachedResult> resultCache = new ConcurrentHashMap<>();

    public RAGRetrievalService(EmbeddingModel embeddingModel, DocumentChunkRepository chunkRepo) {
        this.embeddingModel = embeddingModel;
        this.chunkRepo = chunkRepo;
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

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        float[] vec = queryEmbedding.vector();
        String vecStr = formatVector(vec);

        log.info("RAG query='{}' | vec_dim={} | vec_preview=[{},{}...]",
                truncate(query, 50), vec.length,
                vec.length > 0 ? String.format("%.6f", vec[0]) : "N/A",
                vec.length > 1 ? String.format("%.6f", vec[1]) : "N/A");

        if (log.isDebugEnabled()) {
            long totalChunks = chunkRepo.count();
            long nullCount = chunkRepo.countNullEmbeddings();
            long nonNullCount = chunkRepo.countNonNullEmbeddings();
            log.debug("RAG db_stats: total={}, non_null_embeddings={}, null_embeddings={}",
                    totalChunks, nonNullCount, nullCount);

            if (nonNullCount == 0 && totalChunks > 0) {
                log.error(
                        "RAG CRITICAL: All {} embeddings are NULL! Documents need re-upload after fixing ETL pipeline.",
                        totalChunks);
                return List.of();
            }
        }

        double maxDist = 1.0 - similarityThreshold;
        List<DocumentChunkEntity> rawResults = chunkRepo.findSimilarChunks(null, vecStr, maxDist, topK * 2);
        log.info("RAG threshold_query: maxDist={}, results={}", maxDist, rawResults.size());

        if (rawResults.isEmpty()) {
            List<DocumentChunkEntity> fallbackResults = chunkRepo.findSimilarChunks(null, vecStr, 1.0, topK);
            log.info("RAG fallback (no threshold): results={}", fallbackResults.size());
            if (!fallbackResults.isEmpty()) {
                log.warn("RAG no results at threshold {}, fallback: {} chunks",
                        similarityThreshold, fallbackResults.size());
                rawResults = fallbackResults;
            } else {
                log.warn("RAG returned 0 chunks even without threshold!");
            }
        }

        List<RetrievedChunk> results = rawResults.stream()
                .filter(chunk -> chunk.getContent() != null)
                .limit(topK)
                .map(entity -> {
                    RetrievedChunk rc = new RetrievedChunk();
                    rc.setChunkId((long) entity.getChunkIndex());
                    rc.setContentSnippet(truncateContent(entity.getContent()));
                    rc.setDocumentName(entity.getDocument() != null ? entity.getDocument().getFilename() : "unknown");
                    rc.setContent(entity.getContent());
                    return rc;
                })
                .collect(Collectors.toList());

        log.info("RAG retrieved {} chunks in {}ms for query='{}'", results.size(),
                Duration.between(start, Instant.now()).toMillis(),
                truncate(query, 50));
        return results;
    }

    public String formatContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder("\n\n--- 知识库参考 ---\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append(String.format("[%d] 文档: %s\n%s\n\n", i + 1, c.getDocumentName(), c.getContent()));
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
