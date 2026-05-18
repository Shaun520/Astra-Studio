package com.example.astrastudioopenai.ai;

import com.example.astrastudioopenai.ai.guardrail.SafeInputGuardrail;
import com.example.astrastudioopenai.dto.AiStreamChunk;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.*;
import dev.langchain4j.service.guardrail.InputGuardrails;
import reactor.core.publisher.Flux;
//import dev.langchain4j.service.spring.AiService;

import java.util.List;
import java.util.function.Consumer;

//@AiService
@InputGuardrails({ SafeInputGuardrail.class })
public interface AiCodeHelperService {

    // @SystemMessage(fromResource = "system-prompt.txt")
    ChatResponse chat(dev.langchain4j.data.message.UserMessage message);

    // @SystemMessage(fromResource = "system-prompt.txt")
    // Flux流式响应输出
    TokenStream chatWithStream(
            @MemoryId String memoryId,
            @UserMessage String message);
}
