package com.example.astrastudioopenai.service.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Component;

@Component
public class ConversationCacheService {

    private static final Logger log = LoggerFactory.getLogger(ConversationCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;

    public ConversationCacheService(StringRedisTemplate redisTemplate,
                                    @Value("${conversation.persistence.redis-ttl-hours:24}") int ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = (long) ttlHours * 3600;
    }

    public void cacheContext(String memoryId, byte[] kryoBytes) {
        String key = key(memoryId);
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.set(key.getBytes(), kryoBytes);
                connection.expire(key.getBytes(), ttlSeconds);
                connection.set(versionKey(memoryId).getBytes(), String.valueOf(System.currentTimeMillis()).getBytes());
                return null;
            });
            log.debug("Cached context for memoryId={}, size={} bytes, ttl={}s", memoryId, kryoBytes.length, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to cache context for memoryId={}", memoryId, e);
        }
    }

    public byte[] getCachedBytes(String memoryId) {
        String key = key(memoryId);
        try {
            return redisTemplate.execute((RedisCallback<byte[]>) connection -> connection.get(key.getBytes()));
        } catch (Exception e) {
            log.warn("Failed to read cache for memoryId={}", memoryId, e);
            return null;
        }
    }

    public void invalidate(String memoryId) {
        redisTemplate.delete(key(memoryId));
        redisTemplate.delete(versionKey(memoryId));
        log.debug("Invalidated cache for memoryId={}", memoryId);
    }

    public long getVersion(String memoryId) {
        String val = redisTemplate.opsForValue().get(versionKey(memoryId));
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ignored) { }
        }
        return 0L;
    }

    private String key(String memoryId) {
        return "astra:conv:" + memoryId;
    }

    private String versionKey(String memoryId) {
        return "astra:conv:" + memoryId + ":version";
    }
}
