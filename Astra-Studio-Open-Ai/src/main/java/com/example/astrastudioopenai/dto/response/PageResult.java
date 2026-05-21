package com.example.astrastudioopenai.dto.response;

import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.MessageEntity;
import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int size;

    public static <T> PageResult<T> of(List<T> content, long totalElements, int totalPages, int currentPage, int size) {
        PageResult<T> result = new PageResult<>();
        result.setContent(content);
        result.setTotalElements(totalElements);
        result.setTotalPages(totalPages);
        result.setCurrentPage(currentPage);
        result.setSize(size);
        return result;
    }

    public static PageResult<ConversationEntity> fromSpringPage(org.springframework.data.domain.Page<ConversationEntity> page) {
        return PageResult.of(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    public static PageResult<MessageDTO> fromMessagePage(org.springframework.data.domain.Page<MessageEntity> page) {
        List<MessageDTO> content = page.getContent().stream()
                .map(MessageDTO::fromEntity)
                .toList();

        return PageResult.of(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
