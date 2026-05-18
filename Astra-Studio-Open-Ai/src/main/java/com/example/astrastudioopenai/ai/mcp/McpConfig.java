package com.example.astrastudioopenai.ai.mcp;


import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {
    @Value("${custom.api-keys.bigmodel}")
    private String bigModelApiKey;

    @Bean
    public McpToolProvider mcpToolProvider() {
        // 连接Mcp
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl("https://open.bigmodel.cn/api/mcp/web_search/sse?Authorization=" + bigModelApiKey)
                .logRequests(true)
                .logResponses(true)
                .build();
        // 创建Mcp客户端
        McpClient mcpClient = new DefaultMcpClient.Builder()
                .key("shaunMcpClient")
                .transport(transport)
                .build();
        // 从Mcp 客户端获取工具
        McpToolProvider mcpToolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
        return mcpToolProvider;
    }
}
