package com.example.astrastudioopenai.service.ai;

import com.example.astrastudioopenai.service.ai.guardrail.SafeInputGuardrail;
import com.example.astrastudioopenai.dto.AiStreamChunk;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.*;
import dev.langchain4j.service.guardrail.InputGuardrails;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

@InputGuardrails({ SafeInputGuardrail.class })
public interface AiCodeHelperService {

    ChatResponse chat(dev.langchain4j.data.message.UserMessage message);

    TokenStream chatWithStream(
            @MemoryId String memoryId,
            @UserMessage String message);
}
