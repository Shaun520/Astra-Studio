package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.service.chat.ChatService;
import com.example.astrastudioopenai.service.conversation.ConversationQueryService;
import com.example.astrastudioopenai.service.conversation.ConversationPersistenceService;
import com.example.astrastudioopenai.service.knowledge.KnowledgeService;
import com.example.astrastudioopenai.service.routing.RoutingStatsService;
import com.example.astrastudioopenai.dto.response.RetrievedChunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RequestMapping("/ai")
@RestController
public class AiController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private ConversationQueryService conversationQueryService;

    @Autowired(required = false)
    private ConversationPersistenceService conversationPersistenceService;

    @Autowired
    private RoutingStatsService statsService;

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam("memoryId") String memoryId,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "deepThink", defaultValue = "false") boolean deepThink,
            @RequestParam(value = "webSearch", defaultValue = "false") boolean webSearch,
            @RequestParam(value = "model", defaultValue = "glm-5") String modelName,
            @RequestParam(value = "knowledgeBase", defaultValue = "false") boolean knowledgeBase) {
        return chatService.streamChat(memoryId, text, files, deepThink, webSearch, modelName, knowledgeBase);
    }

    @GetMapping("/routing-stats")
    public ResponseEntity<RoutingStatsService.RoutingStatsRecord> getRoutingStats() {
        return ResponseEntity.ok(statsService.getStats());
    }

    @PostMapping("/knowledge/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName) {
        return knowledgeService.uploadDocument(fileUrl, fileName);
    }

    @PostMapping("/knowledge/search")
    public ResponseEntity<List<RetrievedChunk>> searchKnowledge(
            @RequestBody Map<String, String> body) {
        return knowledgeService.searchKnowledge(body.getOrDefault("query", ""));
    }

    @GetMapping("/conversation/{memoryId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable String memoryId) {
        return conversationQueryService.getConversation(memoryId);
    }

    @GetMapping("/conversations/{memoryId}/restore")
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

    @PostMapping(value = "/knowledge/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importDocument(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName) {
        return knowledgeService.uploadDocument(fileUrl, fileName);
    }

    @GetMapping("/knowledge/documents")
    public ResponseEntity<?> listDocuments(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return knowledgeService.listDocuments(page, size);
    }

    @DeleteMapping("/knowledge/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable("id") Long id) {
        return knowledgeService.deleteDocument(id);
    }

    @GetMapping("/knowledge/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable("id") Long id) {
        return knowledgeService.getDocumentStatus(id);
    }
}
