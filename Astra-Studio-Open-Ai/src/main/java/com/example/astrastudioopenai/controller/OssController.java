package com.example.astrastudioopenai.controller;

import com.example.astrastudioopenai.service.oss.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequestMapping("/oss")
@RestController
public class OssController {

    @Autowired(required = false)
    private OssService ossService;

    @GetMapping("/presign")
    public ResponseEntity<Map<String, Object>> presignUpload(
            @RequestParam("fileName") String fileName) {
        if (ossService == null || !ossService.isAvailable()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "OSS service not available",
                    "enabled", false));
        }
        try {
            Map<String, String> presign = ossService.generatePresignPolicy(fileName);
            return ResponseEntity.ok(Map.copyOf(presign));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
