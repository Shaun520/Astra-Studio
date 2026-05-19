package com.example.astrastudioopenai.dto.response;

import lombok.Data;



@Data
public class TextChunk {

    private int index;
    private String content;
    private int tokenCount;
    private String sourceDocument;
    private float[] embedding;

    public TextChunk(int index, String content, int tokenCount, String sourceDocument) {
        this.index = index;
        this.content = content;
        this.tokenCount = tokenCount;
        this.sourceDocument = sourceDocument;
    }

}
