package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.dto.response.DocumentPageResponse;
import com.example.astrastudioopenai.dto.response.RetrievedChunk;
import com.example.astrastudioopenai.service.knowledge.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RequestMapping("/knowledge")
@RestController
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName) {
        return knowledgeService.uploadDocument(fileUrl, fileName);
    }

    @PostMapping("/search")
    public ResponseEntity<List<RetrievedChunk>> searchKnowledge(
            @RequestBody Map<String, String> body) {
        return knowledgeService.searchKnowledge(body.getOrDefault("query", ""));
    }

    @GetMapping("/documents")
    public ResponseEntity<DocumentPageResponse> listDocuments(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return knowledgeService.listDocuments(page, size);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable("id") Long id) {
        return knowledgeService.getDocumentStatus(id);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable("id") Long id) {
        return knowledgeService.deleteDocument(id);
    }
}
