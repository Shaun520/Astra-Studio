package com.example.astrastudioopenai;

import com.example.astrastudioopenai.ai.AiCodeHelperService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AstraStudioOpenAiApplicationTests {
    @Qualifier("normalAiService")
    @Resource
    private AiCodeHelperService aiCodeHelperService;

    @Resource
    private OpenAiStreamingChatModel openAiStreamingChatModel;

    @Test
    void contextLoads() {
    }

    @Test
    void testOpenAiChatModel() {
        aiCodeHelperService.chat(UserMessage.from(
                TextContent.from("鱼皮编程导航是什么")
     ));
    }



    @Test
    void testAiService() {
        ChatResponse res = aiCodeHelperService.chat(UserMessage.from(
                TextContent.from("湘潭今天天气怎么样")
        ));
        System.out.println(res.aiMessage().text());
    }


    @Test
    void testStream() {
        String userMessage = "你是谁";

         openAiStreamingChatModel.chat(userMessage, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                System.out.println("onPartialResponse: " + partialResponse);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                System.out.println("onPartialThinking: " + partialThinking);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                System.out.println("onPartialToolCall: " + partialToolCall);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                System.out.println("onCompleteToolCall: " + completeToolCall);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                System.out.println("onCompleteResponse: " + completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
            }
        });
    }



}
