package com.example.astrastudioopenai.service.title;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TitleGeneratorServiceTest {

    @Mock
    private OpenAiChatModel openAiChatModel;

    private TitleGeneratorService titleGeneratorService;

    @BeforeEach
    void setUp() throws Exception {
        titleGeneratorService = new TitleGeneratorService();

        setPrivateField(titleGeneratorService, "openAiChatModel", openAiChatModel);
        setPrivateField(titleGeneratorService, "maxLength", 30);
        setPrivateField(titleGeneratorService, "autoGenerateEnabled", true);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("应该使用LLM生成标题")
    void shouldGenerateTitleWithLLM() {
        String userMessage = "帮我写一个Java程序来实现快速排序";
        String assistantReply = "好的，这是一个快速排序的Java实现...";

        ChatResponse mockResponse = mock(ChatResponse.class);
        AiMessage mockAiMessage = mock(AiMessage.class);

        when(openAiChatModel.chat(any(UserMessage.class))).thenReturn(mockResponse);
        when(mockResponse.aiMessage()).thenReturn(mockAiMessage);
        when(mockAiMessage.text()).thenReturn("快速排序算法实现");

        String title = titleGeneratorService.generate(userMessage, assistantReply);

        assertNotNull(title);
        assertEquals("快速排序算法实现", title);
        verify(openAiChatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("LLM返回空时应该使用fallback")
    void shouldUseFallbackWhenLLMReturnsEmpty() {
        String userMessage = "这是一段很长的用户消息用于测试fallback逻辑";

        ChatResponse mockResponse = mock(ChatResponse.class);
        when(openAiChatModel.chat(any(UserMessage.class))).thenReturn(mockResponse);
        when(mockResponse.aiMessage()).thenReturn(null);

        String title = titleGeneratorService.generate(userMessage, "");

        assertNotNull(title);
        assertTrue(title.length() <= 33); // maxLength (30) + "..."
        verify(openAiChatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("LLM调用异常时应该使用fallback")
    void shouldUseFallbackWhenLLMThrowsException() {
        String userMessage = "测试异常处理";

        when(openAiChatModel.chat(any(UserMessage.class))).thenThrow(new RuntimeException("API Error"));

        String title = titleGeneratorService.generate(userMessage, "");

        assertNotNull(title);
        assertFalse(title.isEmpty());
        verify(openAiChatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("空消息应该返回默认标题")
    void shouldReturnDefaultTitleForEmptyMessage() {
        String title = titleGeneratorService.generateFallback("");
        assertEquals("新对话", title);

        title = titleGeneratorService.generateFallback(null);
        assertEquals("新对话", title);
    }

    @Test
    @DisplayName("长文本应该被截断")
    void shouldTruncateLongText() {
        String longMessage = "这是一个非常非常非常非常非常非常非常非常非常非常非常非常长的消息用于测试截断功能";

        ChatResponse mockResponse = mock(ChatResponse.class);
        AiMessage mockAiMessage = mock(AiMessage.class);

        when(openAiChatModel.chat(any(UserMessage.class))).thenReturn(mockResponse);
        when(mockResponse.aiMessage()).thenReturn(mockAiMessage);
        when(mockAiMessage.text()).thenReturn(longMessage);

        String title = titleGeneratorService.generate(longMessage, "");

        assertNotNull(title);
        assertTrue(title.length() <= 30);
    }

    @Test
    @DisplayName("特殊字符应该被清理")
    void shouldCleanSpecialCharacters() {
        String dirtyTitle = "\"测试'标题`清理\"";

        String cleaned = titleGeneratorService.generateFallback(dirtyTitle);

        assertFalse(cleaned.contains("\""));
        assertFalse(cleaned.contains("'"));
        assertFalse(cleaned.contains("`"));
    }
}
