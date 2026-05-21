package com.example.astrastudioopenai.dto.response;

import com.example.astrastudioopenai.entity.ConversationEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class ConversationDTO {
    private Long id;
    private String memoryId;
    private String title;
    private String lastMessagePreview;
    private String modelName;
    private Integer messageCount;
    private Short status;
    private String updatedAt;

    public static ConversationDTO fromEntity(ConversationEntity entity) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(entity.getId());
        dto.setMemoryId(entity.getMemoryId());
        dto.setTitle(entity.getTitle());
        dto.setLastMessagePreview(entity.getLastMessagePreview());
        dto.setModelName(entity.getModelName());
        dto.setMessageCount(entity.getMessageCount());
        dto.setStatus(entity.getStatus());

        if (entity.getUpdatedAt() != null) {
            dto.setUpdatedAt(entity.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return dto;
    }
}
