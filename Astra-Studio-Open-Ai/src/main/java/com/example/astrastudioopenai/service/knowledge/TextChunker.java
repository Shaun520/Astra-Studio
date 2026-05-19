package com.example.astrastudioopenai.service.knowledge;

import com.example.astrastudioopenai.dto.response.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private static final Logger log = LoggerFactory.getLogger(TextChunker.class);

    private final int chunkSize;
    private final int overlap;

    public TextChunker(
            @Value("${knowledge-base.rag.chunk-size:512}") int chunkSize,
            @Value("${knowledge-base.rag.chunk-overlap:64}") int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<TextChunk> chunk(String text, String documentName) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String[] paragraphs = text.split("\n\n+");
        List<String> segments = new ArrayList<>();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() <= chunkSize * 3) {
                segments.add(trimmed);
            } else {
                segments.addAll(recursiveSplit(trimmed));
            }
        }

        StringBuilder overlapBuffer = new StringBuilder();
        int index = 0;
        for (String segment : segments) {
            if (!overlapBuffer.isEmpty()) {
                segment = overlapBuffer.toString() + segment;
            }

            TextChunk chunk = new TextChunk(index++, segment, tokenEstimate(segment), documentName);
            chunks.add(chunk);

            if (segment.length() > overlap) {
                overlapBuffer = new StringBuilder(segment.substring(segment.length() - overlap));
            } else {
                overlapBuffer.setLength(0);
            }
        }

        log.debug("Chunked text into {} chunks (chunkSize={}, overlap={})", chunks.size(), chunkSize, overlap);
        return chunks;
    }

    private List<String> recursiveSplit(String text) {
        List<String> result = new ArrayList<>();
        if (text.length() <= chunkSize) {
            result.add(text);
            return result;
        }

        String[] splitPatterns = {"\n", "。", ".", "！", "!", "？", "?", " ", ""};
        for (String pattern : splitPatterns) {
            if (" ".equals(pattern)) { result.add(text); break; }
            String[] parts = text.split(pattern, -1);
            boolean anyPartExceedsLimit = false;
            for (String part : parts) {
                if (part.length() > chunkSize) { anyPartExceedsLimit = true; break; }
            }
            if (!anyPartExceedsLimit && parts.length > 1) {
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) result.add(trimmed);
                }
                break;
            }
        }
        if (result.isEmpty()) result.add(text);
        return result;
    }

    private int tokenEstimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, (int) Math.ceil(text.length() / 3.0));
    }
}
