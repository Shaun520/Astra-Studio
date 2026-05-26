package com.example.astrastudioopenai.service.tools.document;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class ProcessingResult {
    private final Object data;
    private final Map<String, Object> statistics;
    private final List<String> warnings;

    public ProcessingResult(Object data, Map<String, Object> statistics, List<String> warnings) {
        this.data = data;
        this.statistics = statistics;
        this.warnings = warnings;
    }

}
