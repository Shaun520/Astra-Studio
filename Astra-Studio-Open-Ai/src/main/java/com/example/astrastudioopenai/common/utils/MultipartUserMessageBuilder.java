package com.example.astrastudioopenai.common.utils;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

public final class MultipartUserMessageBuilder {

    private MultipartUserMessageBuilder() {
    }

    public static String buildText(String text, List<String> files) {
        StringBuilder messageText = new StringBuilder();

        if (text != null && !text.isBlank()) {
            messageText.append(text.trim());
        }

        if (files != null && !files.isEmpty()) {
            for (String fileUrl : files) {
                if (fileUrl == null || fileUrl.isBlank()) continue;
                String url = fileUrl.trim();
                if (isImageUrl(url)) {
                    if (!messageText.isEmpty()) messageText.append("\n");
                    messageText.append("[图片: ").append(url).append("]");
                } else {
                    if (!messageText.isEmpty()) messageText.append("\n");
                    messageText.append("[文件: ").append(url).append("]");
                }
            }
        }

        if (messageText.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "请至少提供文字内容或文件URL");
        }

        return messageText.toString();
    }

    public static UserMessage build(String text, List<String> files) {
        List<Content> contents = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            contents.add(TextContent.from(text.trim()));
        }

        if (files != null && !files.isEmpty()) {
            for (String fileUrl : files) {
                if (fileUrl == null || fileUrl.isBlank()) {
                    continue;
                }
                String url = fileUrl.trim();
                try {
                    if (isImageUrl(url)) {
                        contents.add(ImageContent.from(url));
                    } else {
                        contents.add(TextContent.from("用户上传了文件：" + url));
                    }
                } catch (Exception e) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Invalid file URL: " + url, e);
                }
            }
        }

        if (contents.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "请至少提供文字内容或文件URL");
        }

        return UserMessage.from(contents);
    }

    private static boolean isImageUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.contains("/image/")
                || lower.contains("image/");
    }
}
