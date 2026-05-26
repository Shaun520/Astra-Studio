package com.example.astrastudioopenai.service.tools.document;

import lombok.Getter;

@Getter
public class DetectionBox {
    private final String label;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final double confidence;

    public DetectionBox(String label, double x, double y, double width, double height, double confidence) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.confidence = confidence;
    }

}
