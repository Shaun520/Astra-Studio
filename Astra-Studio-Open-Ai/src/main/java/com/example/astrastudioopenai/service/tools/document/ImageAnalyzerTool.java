package com.example.astrastudioopenai.service.tools.document;

import com.example.astrastudioopenai.service.tools.ToolExecutionException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImageAnalyzerTool {
    private static final Logger logger = LoggerFactory.getLogger(ImageAnalyzerTool.class);

    @Value("${image-analyzer.api-key:}")
    private String apiKey;

    @Value("${image-analyzer.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${image-analyzer.model:qwen-vl-max}")
    private String model;

    @Value("${image-analyzer.timeout-ms:30000}")
    private int timeoutMs;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Tool("分析图片，支持URL链接或Base64编码，可用于图片描述、物体检测、OCR文字识别")
    public ImageAnalysisResult analyze(
            @P("图片URL地址（https://...开头）或Base64编码数据，二选一") String imageInput,
            @P("分析类型：caption（图片描述）、detect_objects（物体检测）、ocr（文字识别），默认caption") String taskType)
            throws ToolExecutionException {

        if (imageInput == null || imageInput.trim().isEmpty()) {
            throw new IllegalArgumentException("图片数据不能为空，请提供 URL 或 Base64 数据");
        }

        if (taskType == null || taskType.trim().isEmpty()) {
            taskType = "caption";
        }

        String imageBase64;
        if (imageInput.trim().startsWith("http://") || imageInput.trim().startsWith("https://")) {
            logger.info("Analyzing image from URL: {}, taskType={}", imageInput, taskType);
            imageBase64 = downloadImageAsBase64(imageInput);
        } else {
            logger.info("Analyzing image from Base64: dataLength={}, taskType={}", imageInput.length(), taskType);
            imageBase64 = imageInput;
        }

        try {
            if (apiKey == null || apiKey.isEmpty()) {
                logger.warn("Image analyzer API key not configured, returning mock result");
                return createMockResult(taskType, imageBase64);
            }

            String prompt = buildPrompt(taskType);
            Map<String, Object> requestBody = buildRequestBody(imageBase64, prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ToolExecutionException("analyze_image",
                        "Vision API 调用失败: HTTP " + response.statusCode() + " - " + response.body(), null);
            }

            return parseResponse(response.body(), taskType);

        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("analyze_image", "图片分析失败: " + e.getMessage(), e);
        }
    }

    private String downloadImageAsBase64(String imageUrl) throws ToolExecutionException {
        try {
            logger.info("Downloading image from: {}", imageUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new ToolExecutionException("download_image",
                        "下载图片失败: HTTP " + response.statusCode() + " - URL: " + imageUrl, null);
            }

            byte[] imageData = response.body();
            logger.info("Downloaded image: {} bytes from {}", imageData.length, imageUrl);

            String base64 = Base64.getEncoder().encodeToString(imageData);
            return base64;

        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("download_image",
                    "下载图片失败 (" + imageUrl + "): " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String taskType) {
        return switch (taskType.toLowerCase()) {
            case "caption" -> "请详细描述这张图片的内容，包括主要对象、场景、颜色、构图等。用中文回答。";
            case "detect_objects" -> "请识别这张图片中的所有物体，列出每个物体的名称、位置和置信度。用中文回答，格式为JSON列表。";
            case "ocr" -> "请识别这张图片中的所有文字内容，保持原有格式和语言。如果图片中没有文字，请说明。";
            default -> "请分析这张图片并提供详细的描述。用中文回答。";
        };
    }

    private Map<String, Object> buildRequestBody(String imageBase64, String prompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", 2048);

        Map<String, Object> messageContent = new HashMap<>();
        messageContent.put("type", "image_url");
        messageContent.put("image_url", Map.of(
                "url", "data:image/jpeg;base64," + imageBase64,
                "detail", "auto"));

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);

        List<Object> contentList = List.of(textContent, messageContent);

        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", contentList)));

        return body;
    }

    private ImageAnalysisResult parseResponse(String responseBody, String taskType) {
        try {
            String content = extractJsonValue(responseBody, "content");
            if (content == null || content.isEmpty()) {
                return new ImageAnalysisResult(taskType, "无法获取分析结果", new HashMap<>());
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("rawResponse", responseBody);

            return new ImageAnalysisResult(taskType, content, metadata);

        } catch (Exception e) {
            logger.error("Failed to parse vision API response", e);
            return new ImageAnalysisResult(taskType, "解析响应失败: " + e.getMessage(), new HashMap<>());
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1)
                return null;

            startIndex += searchKey.length();

            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            if (startIndex >= json.length())
                return null;

            char quoteChar = json.charAt(startIndex);
            if (quoteChar == '"') {
                startIndex++;
                StringBuilder sb = new StringBuilder();
                for (int i = startIndex; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '\\' && i + 1 < json.length()) {
                        char next = json.charAt(i + 1);
                        switch (next) {
                            case '"' -> sb.append('"');
                            case '\\' -> sb.append('\\');
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            default -> {
                                sb.append(c);
                                sb.append(next);
                            }
                        }
                        i++;
                    } else if (c == '"') {
                        return sb.toString();
                    } else {
                        sb.append(c);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private ImageAnalysisResult createMockResult(String taskType, String imageBase64) {
        int dataLength = imageBase64.length();
        String mockAnalysis = switch (taskType.toLowerCase()) {
            case "caption" -> "[模拟结果] 这是一张图片（Base64长度: " + dataLength + " 字符）。配置 API Key 后将使用 AI 模型进行真实分析。";
            case "detect_objects" -> "[模拟结果] 检测到未知物体（需要配置 API Key）。";
            case "ocr" -> "[模拟结果] 未识别到文字内容（需要配置 API Key）。";
            default -> "[模拟结果] 图片分析功能需要配置 API Key。";
        };

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("mock", true);
        metadata.put("dataLength", dataLength);

        return new ImageAnalysisResult(taskType, mockAnalysis, metadata);
    }

    private String toJson(Object obj) {
        try {
            StringBuilder sb = new StringBuilder();
            appendJson(sb, obj);
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    private void appendJson(StringBuilder sb, Object obj) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof String) {
            sb.append("\"").append(escapeJson((String) obj)).append("\"");
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj);
        } else if (obj instanceof Map) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                appendJson(sb, entry.getValue());
            }
            sb.append("}");
        } else if (obj instanceof List) {
            sb.append("[");
            boolean first = true;
            for (Object item : (List<?>) obj) {
                if (!first)
                    sb.append(",");
                first = false;
                appendJson(sb, item);
            }
            sb.append("]");
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t");
    }
}