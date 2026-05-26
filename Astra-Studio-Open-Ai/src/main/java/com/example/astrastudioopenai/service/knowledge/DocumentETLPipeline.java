package com.example.astrastudioopenai.service.knowledge;

import com.example.astrastudioopenai.common.utils.FileTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentETLPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentETLPipeline.class);

    private final DocumentParserService parser;
    private final TextChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeDocumentRepository docRepo;
    private final DocumentChunkRepository chunkRepo;
    private final MultimodalEmbeddingService multimodalEmbeddingService;
    private final ObjectMapper objectMapper;

    @Value("${knowledge-base.rag.batch-size:10}")
    private int batchSize;

    @Value("${knowledge-base.multimodal-embedding.dimensions:1024}")
    private int dimensions;

    public DocumentETLPipeline(DocumentParserService parser,
            TextChunker chunker,
            EmbeddingModel embeddingModel,
            KnowledgeDocumentRepository docRepo,
            DocumentChunkRepository chunkRepo,
            MultimodalEmbeddingService multimodalEmbeddingService) {
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.multimodalEmbeddingService = multimodalEmbeddingService;
        this.objectMapper = new ObjectMapper();
    }

    public KnowledgeDocumentEntity processDocument(String fileUrl, String fileName) {
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setFilename(fileName);
        doc.setFileType(parser.detectFileType(fileUrl));
        doc.setFileUrl(fileUrl);
        doc.setStatus("PROCESSING");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        String contentType = detectContentType(fileUrl, fileName);
        doc.setContentType(contentType);
        doc = docRepo.save(doc);

        try {
            if ("image".equals(contentType)) {
                processImageDocument(doc, fileUrl, fileName);
            } else {
                processTextDocument(doc, fileUrl, fileName);
            }

            doc.setStatus("READY");
            log.info("ETL completed: document={}, fileName={}, contentType={}", doc.getId(), fileName, contentType);
        } catch (Exception e) {
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
            log.error("ETL failed for document={}, file={}, contentType={}", doc.getId(), fileName, contentType, e);
        } finally {
            doc.setUpdatedAt(LocalDateTime.now());
            docRepo.save(doc);
        }
        return doc;
    }

    private String detectContentType(String fileUrl, String fileName) {
        return FileTypes.detectContentType(fileName);
    }

    private void processImageDocument(KnowledgeDocumentEntity doc, String fileUrl, String fileName) throws Exception {
        log.info("Processing image document: document={}, fileUrl={}", doc.getId(), fileUrl);

        MultimodalEmbeddingService.ImageMetadata metadata = multimodalEmbeddingService
                .validateAndExtractMetadata(fileUrl);

        float[] embedding = multimodalEmbeddingService.embedImageByUrl(fileUrl);

        String imageDescription = "";
        if (multimodalEmbeddingService.isVisionModelEnabled()) {
            try {
                log.info("正在为图片生成视觉描述: {}", fileName);
                imageDescription = multimodalEmbeddingService.generateImageDescription(fileUrl);
                log.info("图片描述生成完成: length={}", imageDescription.length());
            } catch (Exception e) {
                log.warn("图片描述生成失败，使用默认格式: {}", e.getMessage());
                imageDescription = "";
            }
        }

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("source_type", "image");
        metadataMap.put("image_format", metadata.getFormat());
        metadataMap.put("original_file", fileName);
        metadataMap.put("image_url", fileUrl);
        metadataMap.put("vector_type", "multimodal");
        metadataMap.put("model", "qwen3-vl-embedding");
        metadataMap.put("dimensions", dimensions);
        metadataMap.put("has_vision_description", !imageDescription.isEmpty());
        String metadataJson = objectMapper.writeValueAsString(metadataMap);

        String imageContent;
        if (!imageDescription.isEmpty()) {
            imageContent = imageDescription;
        } else {
            imageContent = "该图片已上传至知识库，可通过跨模态检索匹配相关查询";
        }

        DocumentChunkEntity chunkEntity = new DocumentChunkEntity();
        chunkEntity.setDocument(doc);
        chunkEntity.setChunkIndex(0);
        chunkEntity.setContent(imageContent);
        chunkEntity.setTokenCount(tokenEstimate(imageContent));
        chunkEntity.setEmbedding(embedding);
        chunkEntity.setMetadataJson(metadataJson);
        chunkEntity.setCreatedAt(LocalDateTime.now());

        chunkRepo.save(chunkEntity);
        doc.setChunkCount(1);

        log.info("Image ETL completed: document={}, chunks=1, dimensions={}, hasDesc={}",
                doc.getId(), embedding.length, !imageDescription.isEmpty());
    }

    private void processTextDocument(KnowledgeDocumentEntity doc, String fileUrl, String fileName) throws Exception {
        log.info("Processing text document: document={}, fileUrl={}", doc.getId(), fileUrl);

        String text = parser.parseToText(fileUrl);
        List<TextChunk> chunks = chunker.chunk(text, fileName);
        embedAndStore(doc, chunks);

        doc.setChunkCount(chunks.size());
        log.info("Text ETL completed: document={}, fileName={}, chunks={}", doc.getId(), fileName, chunks.size());
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

    private int tokenEstimate(String text) {
        if (text == null || text.isEmpty())
            return 0;
        return Math.max(1, (int) Math.ceil(text.length() / 3.0));
    }
}
