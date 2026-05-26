package com.example.astrastudioopenai.service.tools.document;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class CodeExecutorTool {

    @Tool("执行代码并返回结果")
    public ExecutionResult execute(
            @P("要执行的代码") String code,
            @P("编程语言: python/javascript/java") String language,
            @P("超时时间(秒)") int timeout) {
        throw new UnsupportedOperationException("Coming soon - 代码执行功能将在未来版本实现");
    }
}
