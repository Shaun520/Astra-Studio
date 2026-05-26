package com.example.astrastudioopenai.service.tools.document;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class DataProcessorTool {

    @Tool("处理和转换数据")
    public ProcessingResult process(
            @P("CSV或JSON格式的数据字符串") String data,
            @P("操作类型: aggregate(统计)/filter(过滤)/sort(排序)/transform(转换)") String operation,
            @P("操作参数(JSON格式)") String params) {
        throw new UnsupportedOperationException("Coming soon - 数据处理功能将在未来版本实现");
    }
}
