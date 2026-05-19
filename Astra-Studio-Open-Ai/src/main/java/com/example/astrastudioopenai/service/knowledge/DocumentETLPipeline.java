package com.example.astrastudioopenai.service.knowledge;

import com.example.astrastudioopenai.entity.DocumentChunkEntity;
import com.example.astrastudioopenai.entity.KnowledgeDocumentEntity;
import com.example.astrastudioopenai.repository.DocumentChunkRepository;
import com.example.astrastudioopenai.repository.KnowledgeDocumentRepository;
import com.example.astrastudioopenai.dto.response.TextChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentETLPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentETLPipeline.class);

    private final DocumentParserService parser;
    private final TextChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeDocumentRepository docRepo;
    private final DocumentChunkRepository chunkRepo;

    @Value("${knowledge-base.rag.batch-size:10}")
    private int batchSize;

    public DocumentETLPipeline(DocumentParserService parser,
            TextChunker chunker,
            EmbeddingModel embeddingModel,
            KnowledgeDocumentRepository docRepo,
            DocumentChunkRepository chunkRepo) {
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
    }

    public KnowledgeDocumentEntity processDocument(String fileUrl, String fileName) {
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setFilename(fileName);
        doc.setFileType(parser.detectFileType(fileUrl));
        doc.setFileUrl(fileUrl);
        doc.setStatus("PROCESSING");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc = docRepo.save(doc);

        try {
            String text = parser.parseToText(fileUrl);
            List<TextChunk> chunks = chunker.chunk(text, fileName);
            embedAndStore(doc, chunks);

            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            log.info("ETL completed: document={}, fileName={}, chunks={}", doc.getId(), fileName, chunks.size());
        } catch (Exception e) {
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            log.error("ETL failed for document={}, file={}", doc.getId(), fileName, e);
        } finally {
            doc.setUpdatedAt(LocalDateTime.now());
            docRepo.save(doc);
        }
        return doc;
    }

    protected void embedAndStore(KnowledgeDocumentEntity doc, List<TextChunk> chunks) {
        List<List<TextChunk>> batches = splitIntoBatches(chunks, batchSize);
        for (int batchIdx = 0; batchIdx < batches.size(); batchIdx++) {
            List<TextChunk> batch = batches.get(batchIdx);
            List<String> texts = batch.stream().map(TextChunk::getContent).toList();

            try {
                List<TextSegment> segments = texts.stream().map(TextSegment::from).collect(Collectors.toList());
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                for (int i = 0; i < batch.size(); i++) {
                    TextChunk tc = batch.get(i);
                    float[] vector = embeddings.get(i).vector();
                    if (vector == null || vector.length == 0) {
                        log.warn("Skipping chunk {} due to null/empty embedding", tc.getIndex());
                        continue;
                    }
                    saveChunk(doc, tc, vector);
                }
                log.debug("Embedded batch {}/{}, size={}", batchIdx + 1, batches.size(), batch.size());
            } catch (Exception e) {
                log.error("Failed to embed batch {} of {}, skipping {} chunks",
                        batchIdx + 1, batches.size(), batch.size(), e);
                throw new RuntimeException("Embedding failed for batch " + (batchIdx + 1), e);
            }
        }
    }

    @Transactional
    protected void saveChunk(KnowledgeDocumentEntity doc, TextChunk tc, float[] embedding) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setDocument(doc);
        entity.setChunkIndex(tc.getIndex());
        entity.setContent(tc.getContent());
        entity.setTokenCount(tc.getTokenCount());

        if (embedding != null) {
            entity.setEmbedding(embedding);
        }
        entity.setCreatedAt(LocalDateTime.now());
        DocumentChunkEntity saved = chunkRepo.save(entity);
        if (saved.getEmbedding() == null && embedding != null) {
            log.error("EMBEDDING PERSISTENCE BUG: chunk_id={}, embedding was set but saved as NULL! " +
                    "Check Hibernate vector mapping configuration.", saved.getId());
        }
    }

    private <T> List<List<T>> splitIntoBatches(List<T> items, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            result.add(items.subList(i, Math.min(i + batchSize, items.size())));
        }
        return result;
    }
}
