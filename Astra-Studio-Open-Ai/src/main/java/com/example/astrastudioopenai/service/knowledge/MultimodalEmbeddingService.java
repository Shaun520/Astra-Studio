package com.example.astrastudioopenai.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MultimodalEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(MultimodalEmbeddingService.class);

    @Value("${knowledge-base.multimodal-embedding.enabled:true}")
    private boolean enabled;

    @Value("${knowledge-base.multimodal-embedding.model-name:tongyi-embedding-vision-plus-2026-03-06}")
    private String modelName;

    @Value("${knowledge-base.multimodal-embedding.dimensions:1024}")
    private int dimensions;

    @Value("${knowledge-base.multimodal-embedding.api-endpoint:https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding}")
    private String apiEndpoint;

    @Value("${knowledge-base.multimodal-embedding.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${knowledge-base.multimodal-embedding.max-retries:3}")
    private int maxRetries;

    @Value("${knowledge-base.multimodal-embedding.instruct:用于RAG检索的图片内容描述}")
    private String instruct;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${knowledge-base.multimodal-embedding.max-file-size:5MB}")
    private String maxFileSizeStr;

    @Value("${knowledge-base.multimodal-embedding.supported-formats:jpg,png,webp}")
    private String supportedFormatsStr;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final int CACHE_MAX_SIZE = 100;
    private static final long CACHE_TTL_MINUTES = 30;
    private final ConcurrentHashMap<String, CacheEntry> embeddingCache = new ConcurrentHashMap<>();

    public float[] embedText(String text) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("Multimodal embedding is disabled");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("文本不能为空");
        }

        String cacheKey = computeCacheKey("text:" + text);
        float[] cached = getFromCache(cacheKey);
        if (cached != null) {
            log.info("命中文本Embedding缓存: text_length={}", text.length());
            return cached;
        }

        Map<String, Object> requestBody = buildTextRequestBody(text);
        float[] vector = callApiWithRetry(requestBody, "text:" + truncate(text, 50));

        putToCache(cacheKey, vector);
        return vector;
    }

    private Map<String, Object> buildTextRequestBody(String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);

        Map<String, Object> input = new HashMap<>();
        Map<String, Object> contentsItem = new HashMap<>();
        contentsItem.put("text", text);
        input.put("contents", new Map[] { contentsItem });
        body.put("input", input);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("dimension", dimensions);
        body.put("parameters", parameters);

        return body;
    }

    private String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private String maskSensitiveData(String json) {
        if (json == null || json.isBlank())
            return json;
        String masked = json.replaceAll("(?i)(\"?api[_-]?key\"?\\s*[:=]\\s*[\"']?)[^\"'\\s,}]{4,}", "$1****");
        masked = masked.replaceAll("(?i)(Authorization\\s*:\\s*Bearer\\s+)[^\\s]+", "$1****");
        masked = masked.replaceAll("(\"(?:image_url|url)\"\\s*:\\s*\")[^\"]+(\")", "$1****$2");
        if (masked.length() > 500) {
            return masked.substring(0, 500) + "...(truncated)";
        }
        return masked;
    }

    private String maskImageUrl(String url) {
        if (url == null || url.isBlank())
            return "(empty)";
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (path != null && path.length() > 30) {
                path = path.substring(0, 15) + "..." + path.substring(path.length() - 10);
            }
            return host != null ? host + path : truncate(url, 40);
        } catch (Exception e) {
            return truncate(url, 40);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float[] embedImageByUrl(String imageUrl) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("Multimodal embedding is disabled");
        }

        validateImageUrl(imageUrl);

        String cacheKey = computeCacheKey(imageUrl);
        float[] cached = getFromCache(cacheKey);
        if (cached != null) {
            log.info("命中缓存: source={}", imageUrl);
            return cached;
        }

        Map<String, Object> requestBody = buildRequestBody(imageUrl);
        float[] vector = callApiWithRetry(requestBody, imageUrl);

        putToCache(cacheKey, vector);
        return vector;
    }

    public float[] embedImageByBase64(byte[] imageBytes, String format) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("Multimodal embedding is disabled");
        }

        validateImageBytes(imageBytes, format);

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUri = "data:image/" + format + ";base64," + base64Image;

        String cacheKey = computeCacheKey(dataUri);
        float[] cached = getFromCache(cacheKey);
        if (cached != null) {
            log.info("命中缓存: source=base64:{}", format);
            return cached;
        }

        Map<String, Object> requestBody = buildRequestBody(dataUri);
        float[] vector = callApiWithRetry(requestBody, "base64:" + format);

        putToCache(cacheKey, vector);
        return vector;
    }

    public ImageMetadata validateAndExtractMetadata(String imageUrl) throws Exception {
        validateImageUrl(imageUrl);

        HttpClient downloadClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<byte[]> response = downloadClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IllegalArgumentException("无法访问图片URL，HTTP状态码: " + response.statusCode());
        }

        byte[] imageBytes = response.body();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("无法读取图片数据");
        }

        validateFileSize(imageBytes.length);

        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        int width = image != null ? image.getWidth() : 0;
        int height = image != null ? image.getHeight() : 0;

        String contentType = response.headers().firstValue("Content-Type").orElse("image/png");

        ImageMetadata metadata = new ImageMetadata();
        metadata.setMimeType(contentType);
        metadata.setFileSize(imageBytes.length);
        metadata.setFormat(extractFormatFromMimeType(contentType));
        metadata.setWidth(width);
        metadata.setHeight(height);

        log.info("图片元信息: url={}, format={}x{}, size={}KB",
                truncate(imageUrl, 50), width, height, imageBytes.length / 1024);

        return metadata;
    }

    private void validateImageUrl(String imageUrl) throws Exception {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("图片URL不能为空");
        }

        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            throw new IllegalArgumentException("图片URL必须以 http:// 或 https:// 开头");
        }
    }

    private void validateImageBytes(byte[] imageBytes, String format) throws Exception {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("图片数据不能为空");
        }

        long maxSize = parseFileSize(maxFileSizeStr);
        if (imageBytes.length > maxSize) {
            throw new IllegalArgumentException("图片文件过大，限制 " + maxFileSizeStr);
        }

        String[] supportedFormats = supportedFormatsStr.split(",");
        boolean supported = false;
        for (String fmt : supportedFormats) {
            if (fmt.trim().equalsIgnoreCase(format)) {
                supported = true;
                break;
            }
        }

        if (!supported) {
            throw new IllegalArgumentException("不支持的图片格式: " + format + "，支持的格式: " + supportedFormatsStr);
        }
    }

    private void validateMimeType(String mimeType) throws Exception {
        String[] allowedMimeTypes = { "image/jpeg", "image/png", "image/webp" };
        for (String allowed : allowedMimeTypes) {
            if (allowed.equalsIgnoreCase(mimeType)) {
                return;
            }
        }
        throw new IllegalArgumentException("不支持的图片 MIME 类型: " + mimeType);
    }

    private void validateFileSize(long fileSize) throws Exception {
        long maxSize = parseFileSize(maxFileSizeStr);
        if (fileSize > 0 && fileSize > maxSize) {
            throw new IllegalArgumentException(
                    "图片文件过大: " + fileSize + " bytes，限制 " + maxSize + " bytes (" + maxFileSizeStr + ")");
        }
    }

    private String extractFormatFromMimeType(String mimeType) {
        switch (mimeType.toLowerCase()) {
            case "image/jpeg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/webp":
                return "webp";
            default:
                return mimeType.split("/")[1];
        }
    }

    private long parseFileSize(String sizeStr) {
        sizeStr = sizeStr.trim().toUpperCase();
        if (sizeStr.endsWith("MB")) {
            return Long.parseLong(sizeStr.replace("MB", "")) * 1024 * 1024;
        } else if (sizeStr.endsWith("KB")) {
            return Long.parseLong(sizeStr.replace("KB", "")) * 1024;
        } else if (sizeStr.endsWith("B")) {
            return Long.parseLong(sizeStr.replace("B", ""));
        } else {
            return Long.parseLong(sizeStr);
        }
    }

    private Map<String, Object> buildRequestBody(String imageData) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);

        Map<String, Object> input = new HashMap<>();
        Map<String, Object> contentsItem = new HashMap<>();
        contentsItem.put("image", imageData);
        input.put("contents", new Map[] { contentsItem });
        body.put("input", input);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("dimension", dimensions);
        parameters.put("instruct", instruct);
        body.put("parameters", parameters);

        return body;
    }

    private float[] callApiWithRetry(Map<String, Object> requestBody, String sourceInfo) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return callApi(requestBody, sourceInfo);
            } catch (Exception e) {
                lastException = e;

                if (attempt < maxRetries - 1) {
                    long waitMs = (long) Math.pow(2, attempt) * 1000;
                    log.warn("Multimodal embedding API 调用失败（第{}次重试），等待 {}ms 后重试: {}",
                            attempt + 1, waitMs, e.getMessage());
                    Thread.sleep(waitMs);

                    if (e.getMessage() != null && e.getMessage().contains("401") ||
                            e.getMessage() != null && e.getMessage().contains("403")) {
                        log.error("认证失败，停止重试: {}", e.getMessage());
                        break;
                    }
                }
            }
        }

        throw lastException;
    }

    private float[] callApi(Map<String, Object> requestBody, String sourceInfo) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        log.info("========== 多模态 Embedding API 调用开始 ==========");
        log.info("请求URL: {}", apiEndpoint);
        log.info("配置的期望维度: {}", dimensions);
        log.debug("请求体内容(已脱敏): {}", maskSensitiveData(jsonBody));
        log.info("=====================================================");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("API响应状态码: {}", response.statusCode());
        log.debug("API响应体: {}", response.body());
        log.info("========== 多模态 Embedding API 调用结束 ==========\n");

        if (response.statusCode() == 200) {
            return parseResponse(response.body(), sourceInfo);
        } else {
            handleErrorResponse(response, sourceInfo);
            throw new RuntimeException("API调用失败，状态码: " + response.statusCode());
        }
    }

    private float[] parseResponse(String responseBody, String sourceInfo) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode output = root.path("output");
        JsonNode embeddings = output.path("embeddings");

        if (embeddings.isEmpty() || !embeddings.isArray()) {
            throw new RuntimeException("API返回的embeddings为空或格式错误");
        }

        JsonNode firstEmbedding = embeddings.get(0);
        JsonNode embeddingArray = firstEmbedding.path("embedding");

        if (!embeddingArray.isArray() || embeddingArray.size() == 0) {
            throw new RuntimeException("API返回的embedding数组为空");
        }

        int actualDimensions = embeddingArray.size();
        float[] fullVector = new float[actualDimensions];
        for (int i = 0; i < actualDimensions; i++) {
            fullVector[i] = (float) embeddingArray.get(i).asDouble();
        }

        float[] vector;
        if (actualDimensions == dimensions) {
            vector = fullVector;
            log.info("多模态 Embedding 成功: source={}, dimensions={}", sourceInfo, actualDimensions);
        } else if (actualDimensions > dimensions) {
            vector = truncateVector(fullVector, dimensions);
            log.warn("多模态 Embedding 成功但需降维处理: source={}, 原始维度={}, 降维至={} (建议检查API配置)", sourceInfo, actualDimensions,
                    dimensions);
        } else {
            log.error("向量维度不匹配！期望 {} 维，实际 {} 维（无法升维），source={}", dimensions, actualDimensions, sourceInfo);
            throw new RuntimeException("向量维度不匹配：期望" + dimensions + "维，实际" + actualDimensions + "维");
        }

        return vector;
    }

    private float[] truncateVector(float[] sourceVector, int targetDimensions) {
        if (targetDimensions >= sourceVector.length) {
            return sourceVector.clone();
        }

        float[] result = new float[targetDimensions];
        System.arraycopy(sourceVector, 0, result, 0, targetDimensions);

        return result;
    }

    private void handleErrorResponse(HttpResponse<String> response, String sourceInfo) throws Exception {
        String body = response.body();
        int statusCode = response.statusCode();

        log.error("多模态 Embedding API 错误: status={}, source={}, body={}", statusCode, sourceInfo, body);

        switch (statusCode) {
            case 400:
                throw new RuntimeException("请求参数错误: " + extractErrorMessage(body));
            case 401:
            case 403:
                throw new RuntimeException("认证失败: API Key 无效或权限不足");
            case 429:
                throw new RuntimeException("请求频率超限: 请稍后重试");
            case 500:
            case 502:
            case 503:
                throw new RuntimeException("服务器内部错误: DashScope 服务暂时不可用");
            default:
                throw new RuntimeException("未知错误 (HTTP " + statusCode + "): " + body);
        }
    }

    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("message");
            if (!message.isMissingNode()) {
                return message.asText();
            }
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                return error.path("message").asText(error.toString());
            }
        } catch (Exception e) {
            log.warn("解析错误消息失败", e);
        }
        return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
    }

    private String computeCacheKey(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算缓存键失败，使用原始输入作为键", e);
            return input;
        }
    }

    private float[] getFromCache(String cacheKey) {
        CacheEntry entry = embeddingCache.get(cacheKey);
        if (entry == null) {
            return null;
        }

        if (Instant.now().isAfter(entry.expiryTime)) {
            embeddingCache.remove(cacheKey);
            log.debug("缓存已过期: key={}", cacheKey);
            return null;
        }

        return entry.vector;
    }

    private void putToCache(String cacheKey, float[] vector) {
        if (embeddingCache.size() >= CACHE_MAX_SIZE) {
            evictOldestEntry();
        }

        CacheEntry entry = new CacheEntry();
        entry.vector = vector;
        entry.createdAt = Instant.now();
        entry.expiryTime = Instant.now().plus(java.time.Duration.ofMinutes(CACHE_TTL_MINUTES));

        embeddingCache.put(cacheKey, entry);
        log.debug("添加到缓存: key={}, ttl={}分钟, 当前缓存大小={}", cacheKey, CACHE_TTL_MINUTES, embeddingCache.size());
    }

    private void evictOldestEntry() {
        String oldestKey = null;
        Instant oldestTime = Instant.now();

        for (Map.Entry<String, CacheEntry> entry : embeddingCache.entrySet()) {
            if (entry.getValue().createdAt.isBefore(oldestTime)) {
                oldestTime = entry.getValue().createdAt;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            embeddingCache.remove(oldestKey);
            log.debug("淘汰最旧的缓存条目: key={}", oldestKey);
        }
    }

    public void clearCache() {
        int size = embeddingCache.size();
        embeddingCache.clear();
        log.info("清除所有缓存条目: count={}", size);
    }

    public int getCacheSize() {
        return embeddingCache.size();
    }

    private static class CacheEntry {
        float[] vector;
        Instant createdAt;
        Instant expiryTime;
    }

    public static class ImageMetadata {
        private String mimeType;
        private long fileSize;
        private String format;
        private int width;
        private int height;

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }

    @Value("${knowledge-base.vision-model.enabled:true}")
    private boolean visionModelEnabled;

    @Value("${knowledge-base.vision-model.model-name:qwen-vl-max-latest}")
    private String visionModelName;

    @Value("${knowledge-base.vision-model.api-endpoint:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String visionApiEndpoint;

    public boolean isVisionModelEnabled() {
        return visionModelEnabled;
    }

    public String generateImageDescription(String imageUrl) throws Exception {
        if (!visionModelEnabled) {
            log.info("视觉描述生成已禁用，返回默认描述");
            return "该图片已上传至知识库，可通过跨模态检索匹配相关查询";
        }

        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("图片URL不能为空");
        }

        String cacheKey = computeCacheKey("desc:" + imageUrl);
        float[] cached = getFromCache(cacheKey);
        if (cached != null) {
            return "（缓存）图片内容已通过视觉模型分析并编码";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", visionModelName);

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> contentList = new java.util.ArrayList<>();

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, Object> imageUrlMap = new HashMap<>();
        imageUrlMap.put("url", imageUrl);
        imageContent.put("image_url", imageUrlMap);
        contentList.add(imageContent);

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text",
                "请将以下视觉信息转换为标准的文档格式输出。要求：1. 直接陈述事实和知识点，不要提及'图片''图表''截图'等词汇 2. 使用正式的文档语言风格 3. 按主题分段组织内容 4. 保留所有关键数据、术语和概念 5. 字数控制在300字以内。直接输出文档内容，不要加任何前缀说明。");
        contentList.add(textContent);

        userMessage.put("content", contentList);
        messages.add(userMessage);
        body.put("messages", messages);

        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("max_tokens", 500);
        body.putAll(extraBody);

        String jsonBody = objectMapper.writeValueAsString(body);

        log.info("========== 视觉描述 API 调用开始 ==========");
        log.info("请求URL: {}", visionApiEndpoint);
        log.info("模型: {}", visionModelName);
        log.info("图片URL(已脱敏): {}", maskImageUrl(imageUrl));
        log.debug("请求体内容(已脱敏): {}", maskSensitiveData(jsonBody));
        log.info("=============================================");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(visionApiEndpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("视觉API响应状态码: {}", response.statusCode());
        log.debug("视觉API响应体: {}", response.body());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isEmpty()) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                JsonNode content = message.path("content");
                if (!content.isMissingNode()) {
                    String description = content.asText().trim();
                    log.info("视觉描述生成成功: length={}", description.length());
                    log.debug("描述内容: {}", description);
                    putToCache(cacheKey, new float[] { 1 });
                    return description;
                }
            }
            throw new RuntimeException("视觉API响应格式错误: " + response.body());
        } else {
            handleErrorResponse(response, "generateDescription");
            throw new RuntimeException("视觉描述生成失败，状态码: " + response.statusCode());
        }
    }
}
