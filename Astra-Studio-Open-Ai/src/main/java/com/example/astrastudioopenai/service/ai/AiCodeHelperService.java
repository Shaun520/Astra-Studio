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
@SystemMessage("""
        你是 Astra Studio 的 AI 助手，一个多模态 AI 创作工作台。
        请始终用自然语言直接回答用户的问题，不要输出 JSON、伪代码或任何形式的函数/工具调用文本（例如 call {"function": ...}）。
        只有当系统确实为你提供了对应工具时，才可以通过工具调用机制使用它；
        如果问题需要实时数据（如天气、股价、新闻）而你没有可用工具，请直接说明无法获取实时信息，或基于已有知识作答，不要编造工具名称。
        """)
public interface AiCodeHelperService {

    ChatResponse chat(dev.langchain4j.data.message.UserMessage message);

    TokenStream chatWithStream(
            @MemoryId String memoryId,
            @UserMessage String message);
}
