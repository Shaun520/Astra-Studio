package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.service.conversation.ConversationPersistenceService;
import com.example.astrastudioopenai.service.conversation.ConversationQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequestMapping("/conversation")
@RestController
public class ConversationController {

    @Autowired
    private ConversationQueryService conversationQueryService;

    @Autowired(required = false)
    private ConversationPersistenceService conversationPersistenceService;

    @GetMapping("/{memoryId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable String memoryId) {
        return conversationQueryService.getConversation(memoryId);
    }

    @GetMapping("/{memoryId}/restore")
    public ResponseEntity<Map<String, Object>> restoreConversation(@PathVariable String memoryId) {
        if (conversationPersistenceService == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Conversation persistence not available",
                    "enabled", false));
        }
        var ctx = conversationPersistenceService.restoreOrCreate(memoryId);
        return ResponseEntity.ok(Map.of(
                "memoryId", memoryId,
                "messages", ctx.getMessages(),
                "version", ctx.getVersion(),
                "recovered", !ctx.isEmpty()));
    }
}
