package com.example.astrastudioopenai.dto.response;

import com.example.astrastudioopenai.entity.MessageEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Data
public class MessageDTO {
    private Long id;
    private String role;
    private String content;
    private String thinkingContent;
    private List<String> attachments;
    private Integer sequenceNum;
    private String timestamp;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static MessageDTO fromEntity(MessageEntity entity) {
        MessageDTO dto = new MessageDTO();
        dto.setId(entity.getId());
        dto.setRole(entity.getRole());
        dto.setContent(entity.getContent());
        dto.setThinkingContent(entity.getThinkingContent());
        dto.setSequenceNum(entity.getSequenceNum());

        if (entity.getAttachmentsJson() != null && !entity.getAttachmentsJson().isEmpty()) {
            try {
                dto.setAttachments(objectMapper.readValue(
                        entity.getAttachmentsJson(),
                        new TypeReference<List<String>>() {}
                ));
            } catch (Exception e) {
                dto.setAttachments(Collections.emptyList());
            }
        } else {
            dto.setAttachments(null);
        }

        if (entity.getCreatedAt() != null) {
            dto.setTimestamp(entity.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return dto;
    }
}
