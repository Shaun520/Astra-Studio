package com.example.astrastudioopenai.service.conversation;

import com.example.astrastudioopenai.service.conversation.ConversationPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ConversationQueryService {

    @Autowired(required = false)
    private ConversationPersistenceService conversationPersistenceService;

    public ResponseEntity<Map<String, Object>> getConversation(String memoryId) {
        if (conversationPersistenceService == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Conversation persistence not available",
                    "enabled", false));
        }
        var ctx = conversationPersistenceService.loadContext(memoryId);
        return ResponseEntity.ok(Map.of(
                "memoryId", memoryId,
                "messageCount", ctx.getMessageCount(),
                "version", ctx.getVersion(),
                "isEmpty", ctx.isEmpty()));
    }

    public boolean isAvailable() {
        return conversationPersistenceService != null;
    }
}
