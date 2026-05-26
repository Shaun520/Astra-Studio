package com.example.astrastudioopenai.dto.response;

import com.example.astrastudioopenai.common.utils.FileTypes;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import lombok.Data;

@Data
public class RetrievedChunk {

    private Long chunkId;
    private String contentSnippet;
    private String documentName;
    private double score;
    private Integer pageNumber;
    private String content;

    public static Content toTextContent(RetrievedChunk chunk) {
        StringBuilder sb = new StringBuilder();
        if (chunk.getDocumentName() != null) {
            boolean isImage = FileTypes.isImageFile(chunk.getDocumentName());
            if (!isImage) {
                sb.append("[文档:").append(chunk.getDocumentName());
                if (chunk.getPageNumber() != null) {
                    sb.append(", 页码:").append(chunk.getPageNumber());
                }
                sb.append("] ");
            }
        }
        sb.append(chunk.getContent() != null ? chunk.getContent() : chunk.getContentSnippet());
        return TextContent.from(sb.toString());
    }
}
