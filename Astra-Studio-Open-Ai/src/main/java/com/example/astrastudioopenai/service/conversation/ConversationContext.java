package com.example.astrastudioopenai.service.conversation;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class ConversationContext {

    private String memoryId;
    private List<com.example.astrastudioopenai.dto.response.MessageEntry> messages = new ArrayList<>();
    private String modelName;
    private long version = 1;
    private String checksum;
    private int kvSize;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public static ConversationContext empty(String memoryId) {
        return new ConversationContext(memoryId, Collections.emptyList(), null);
    }

    public ConversationContext() {
    }

    public ConversationContext(String memoryId, List<com.example.astrastudioopenai.dto.response.MessageEntry> messages, String modelName) {
        this.memoryId = memoryId;
        this.messages = messages != null ? messages : new ArrayList<>();
        this.modelName = modelName;
        this.updatedAt = LocalDateTime.now();
        if (this.messages.isEmpty()) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public void addMessage(com.example.astrastudioopenai.dto.response.MessageEntry entry) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(entry);
        version++;
        updatedAt = LocalDateTime.now();
    }

    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }

    public boolean isEmpty() {
        return messages == null || messages.isEmpty();
    }
}
