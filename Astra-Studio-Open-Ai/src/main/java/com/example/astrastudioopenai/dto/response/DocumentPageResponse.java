package com.example.astrastudioopenai.dto.response;

import com.example.astrastudioopenai.entity.KnowledgeDocumentEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPageResponse {
    private List<KnowledgeDocumentEntity> content;
    private PageableInfo pageable;
    private boolean last;
    private int totalPages;
    private long totalElements;
    private int size;
    private int number;
    private boolean first;
    private int numberOfElements;
    private boolean empty;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageableInfo {
        private int pageNumber;
        private int pageSize;
        private String sort;
    }
}
