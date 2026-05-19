package com.example.astrastudioopenai.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class MessageEntry {

    private String role;
    private String content;
    private String thinkingContent;
    private String attachmentsJson;
    private int sequenceNum;
    private LocalDateTime timestamp;

    public MessageEntry() {
    }

    public MessageEntry(String role, String content, int sequenceNum) {
        this.role = role;
        this.content = content;
        this.sequenceNum = sequenceNum;
        this.timestamp = LocalDateTime.now();
    }

}
