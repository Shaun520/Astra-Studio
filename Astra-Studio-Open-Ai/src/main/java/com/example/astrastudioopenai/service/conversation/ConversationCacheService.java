package com.example.astrastudioopenai.service.conversation;

import com.example.astrastudioopenai.dto.response.PageResult;
import com.example.astrastudioopenai.entity.ConversationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "astra:conv:list:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    public String buildCacheKey(int page, int size, String keyword) {
        String key = CACHE_PREFIX + "page:" + page + ":size:" + size;
        if (keyword != null && !keyword.isBlank()) {
            key += ":keyword:" + keyword.trim().toLowerCase();
        }
        return key;
    }

    public void cacheConversationList(int page, int size, String keyword, PageResult<ConversationEntity> result) {
        try {
            String cacheKey = buildCacheKey(page, size, keyword);
            String jsonValue = objectMapper.writeValueAsString(result);

            log.debug("💾 Caching conversation list: key={}, items={}", cacheKey, result.getContent().size());
            redisTemplate.opsForValue().set(cacheKey, jsonValue, DEFAULT_TTL);
        } catch (Exception e) {
            log.warn("⚠️ Failed to cache conversation list: {}", e.getMessage());
        }
    }

    public Optional<PageResult<ConversationEntity>> getCachedConversationList(int page, int size, String keyword) {
        try {
            String cacheKey = buildCacheKey(page, size, keyword);
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);

            if (cachedValue != null) {
                log.debug("🎯 Cache HIT: key={}", cacheKey);
                PageResult<ConversationEntity> result = objectMapper.readValue(
                        cachedValue,
                        objectMapper.getTypeFactory().constructParametricType(PageResult.class,
                                ConversationEntity.class));
                return Optional.of(result);
            }

            log.debug("🎯 Cache MISS: key={}", cacheKey);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("⚠️ Failed to read cached conversation list: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void invalidateConversationListCache() {
        try {
            var keys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                log.info("🗑️ Invalidated {} conversation list cache entries", deletedCount);
            }
        } catch (Exception e) {
            log.error("❌ Failed to invalidate conversation list cache: {}", e.getMessage(), e);
        }
    }

    public void invalidateConversationListCache(String keyword) {
        try {
            String pattern = CACHE_PREFIX + "*:keyword:" + keyword.toLowerCase() + "*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                log.info("🗑️ Invalidated {} cache entries for keyword='{}'", deletedCount, keyword);
            }
        } catch (Exception e) {
            log.error("❌ Failed to invalidate cache for keyword '{}': {}", keyword, e.getMessage(), e);
        }
    }

    public long getCacheSize() {
        try {
            var keys = redisTemplate.keys(CACHE_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("❌ Failed to get cache size: {}", e.getMessage());
            return 0;
        }
    }

    public void clearAllCache() {
        try {
            var keys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                log.info("🧹 Cleared all {} conversation cache entries", deletedCount);
            }
        } catch (Exception e) {
            log.error("❌ Failed to clear cache: {}", e.getMessage(), e);
        }
    }

    private static final String CONTEXT_CACHE_PREFIX = "astra:conv:context:";
    private static final Duration CONTEXT_CACHE_TTL = Duration.ofHours(24);

    public void cacheContext(String memoryId, byte[] serializedContext) {
        try {
            String cacheKey = CONTEXT_CACHE_PREFIX + memoryId;
            log.debug("💾 Caching context: key={}, size={} bytes", cacheKey, serializedContext.length);
            redisTemplate.opsForValue().set(cacheKey, java.util.Base64.getEncoder().encodeToString(serializedContext),
                    CONTEXT_CACHE_TTL);
        } catch (Exception e) {
            log.warn("⚠️ Failed to cache context for memoryId={}: {}", memoryId, e.getMessage());
        }
    }

    public byte[] getCachedBytes(String memoryId) {
        try {
            String cacheKey = CONTEXT_CACHE_PREFIX + memoryId;
            String cachedBase64 = redisTemplate.opsForValue().get(cacheKey);

            if (cachedBase64 != null && !cachedBase64.isEmpty()) {
                log.debug("🎯 Context cache HIT: key={}", cacheKey);
                return java.util.Base64.getDecoder().decode(cachedBase64);
            }

            log.debug("🎯 Context cache MISS: key={}", cacheKey);
            return null;
        } catch (Exception e) {
            log.warn("⚠️ Failed to read cached context for memoryId={}: {}", memoryId, e.getMessage());
            return null;
        }
    }

    public void invalidateContextCache(String memoryId) {
        try {
            String cacheKey = CONTEXT_CACHE_PREFIX + memoryId;
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("🗑️ Invalidated context cache for memoryId={}", memoryId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to invalidate context cache for memoryId={}: {}", memoryId, e.getMessage(), e);
        }
    }
}
