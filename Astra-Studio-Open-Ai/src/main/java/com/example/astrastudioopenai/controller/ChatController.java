package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.service.chat.ChatService;
import com.example.astrastudioopenai.service.routing.RoutingStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private RoutingStatsService statsService;

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam("memoryId") String memoryId,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "deepThink", defaultValue = "false") boolean deepThink,
            @RequestParam(value = "webSearch", defaultValue = "false") boolean webSearch,
            @RequestParam(value = "model", defaultValue = "glm-5.1") String modelName,
            @RequestParam(value = "knowledgeBase", defaultValue = "false") boolean knowledgeBase,
            @RequestParam(value = "selectedTools", required = false) List<String> selectedTools,
            @RequestParam(value = "temperature", defaultValue = "0.7") Double temperature,
            @RequestParam(value = "maxTokens", defaultValue = "4096") Integer maxTokens,
            @RequestParam(value = "topP", defaultValue = "0.95") Double topP,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt) {
        return chatService.streamChat(memoryId, text, files, deepThink, webSearch, modelName, knowledgeBase,
                selectedTools, temperature, maxTokens, topP, systemPrompt);
    }

    @GetMapping("/routing-stats")
    public ResponseEntity<RoutingStatsService.RoutingStatsRecord> getRoutingStats() {
        return ResponseEntity.ok(statsService.getStats());
    }
}
