package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.dto.request.CreateConversationRequest;
import com.example.astrastudioopenai.dto.request.UpdateTitleRequest;
import com.example.astrastudioopenai.dto.response.ConversationDTO;
import com.example.astrastudioopenai.dto.response.MessageDTO;
import com.example.astrastudioopenai.dto.response.PageResult;
import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.service.conversation.ConversationPersistenceService;
import com.example.astrastudioopenai.service.conversation.ConversationQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;

@RequestMapping("/conversation")
@RestController
public class ConversationController {

    @Autowired
    private ConversationQueryService conversationQueryService;

    @Lazy
    @Autowired(required = false)
    private ConversationPersistenceService conversationPersistenceService;

    @Value("${conversation.default-model:deepseek-v4-flash}")
    private String defaultModelName;

    @PostMapping
    public ResponseEntity<ConversationDTO> createConversation(
            @RequestBody(required = false) CreateConversationRequest request) {

        String memoryId = (request != null && request.getMemoryId() != null)
                ? request.getMemoryId()
                : "conv_" + UUID.randomUUID().toString().replace("-", "");

        if (memoryId.length() > 100) {
            throw new IllegalArgumentException("memoryId length cannot exceed 100 characters");
        }

        if (memoryId.trim().isEmpty()) {
            throw new IllegalArgumentException("memoryId cannot be empty or blank");
        }

        String modelName = (request != null && request.getModelName() != null)
                ? request.getModelName()
                : defaultModelName;

        ConversationEntity conv = conversationQueryService.createConversation(memoryId, modelName);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationDTO.fromEntity(conv));
    }

    @GetMapping
    public ResponseEntity<PageResult<ConversationEntity>> listConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {

        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        PageResult<ConversationEntity> result = conversationQueryService.listConversations(page, size, keyword);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{memoryId}/messages")
    public ResponseEntity<PageResult<MessageDTO>> getConversationMessages(
            @PathVariable String memoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String role) {

        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        PageResult<MessageDTO> result = conversationQueryService.getMessages(memoryId, page, size, role);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{memoryId}/title")
    public ResponseEntity<Void> updateTitle(
            @PathVariable String memoryId,
            @Valid @RequestBody UpdateTitleRequest body) {

        conversationQueryService.updateTitle(memoryId, body.getTitle().trim());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String memoryId) {
        conversationQueryService.softDelete(memoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{memoryId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable String memoryId) {
        Map<String, Object> result = conversationQueryService.getConversation(memoryId);
        return ResponseEntity.ok(result);
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
