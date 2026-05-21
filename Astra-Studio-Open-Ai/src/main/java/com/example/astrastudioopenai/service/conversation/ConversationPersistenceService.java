package com.example.astrastudioopenai.service.conversation;

import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.MessageEntity;
import com.example.astrastudioopenai.entity.SnapshotEntity;
import com.example.astrastudioopenai.repository.ConversationRepository;
import com.example.astrastudioopenai.repository.MessageRepository;
import com.example.astrastudioopenai.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ConversationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ConversationPersistenceService.class);

    private final KryoSerializer kryoSerializer;
    private final ConversationCacheService cacheService;
    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final SnapshotRepository snapshotRepo;
    private final ConversationQueryService conversationQueryService;

    @Value("${conversation.persistence.enabled:true}")
    private boolean enabled;

    private final ConcurrentHashMap<String, Object> memoryIdLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    public ConversationPersistenceService(KryoSerializer kryoSerializer,
            @Autowired(required = false) ConversationCacheService cacheService,
            ConversationRepository conversationRepo,
            MessageRepository messageRepo,
            SnapshotRepository snapshotRepo,
            ConversationQueryService conversationQueryService) {
        this.kryoSerializer = kryoSerializer;
        this.cacheService = cacheService;
        if (cacheService == null) {
            log.info("ConversationCacheService not available (Redis not configured), skipping L1 cache");
        }
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.snapshotRepo = snapshotRepo;
        this.conversationQueryService = conversationQueryService;
    }

    @Transactional
    public void saveContext(String memoryId, ConversationContext ctx) {
        if (!enabled || ctx == null)
            return;

        log.info("💾 saveContext called: memoryId={}, msgCount={}", memoryId, ctx.getMessageCount());

        byte[] bytes = kryoSerializer.toBytes(ctx);
        String checksum = kryoSerializer.computeChecksum(bytes);

        ctx.setChecksum(checksum);
        ctx.setKvSize(bytes.length);

        if (cacheService != null) {
            cacheService.cacheContext(memoryId, bytes);
        }

        try {
            conversationQueryService.incrementMessageCount(memoryId);
            String lastPreview = extractLastMessagePreview(ctx);
            if (lastPreview != null) {
                conversationQueryService.updateLastMessagePreview(memoryId, lastPreview);
            }
        } catch (Exception e) {
            log.warn("Failed to update metadata for memoryId: {}", memoryId, e);
        }

        flushToDbInternal(memoryId, ctx, bytes, checksum);
    }

    @Transactional
    public void saveUserMessage(String memoryId, String userMessageText) {
        saveMessage(memoryId, "user", userMessageText, null);
    }

    @Transactional
    public void saveAssistantMessage(String memoryId, String assistantContent, String thinkingContent) {
        saveMessage(memoryId, "assistant", assistantContent, thinkingContent);
    }

    @Transactional
    public void saveMessage(String memoryId, String role, String content, String thinkingContent) {
        if (!enabled)
            return;

        Object lock = memoryIdLocks.computeIfAbsent(memoryId, k -> new Object());

        synchronized (lock) {
            log.info("💬 Saving {} message: memoryId={}", role, memoryId);

            try {
                ConversationEntity conv = getOrCreateConversation(memoryId, content);

                int seqNum = getNextSequenceNumber(memoryId);

                MessageEntity message = buildMessageEntity(conv, role, content, thinkingContent, seqNum);
                messageRepo.save(message);

                updateConversationMetadata(conv, content);

                conversationQueryService.updateLastMessagePreview(memoryId, content);

                log.info("✅ {} message saved: memoryId={}, seqNum={}", role, memoryId, seqNum);
            } catch (Exception e) {
                log.error("❌ Failed to save {} message for memoryId={}: {}", role, memoryId, e.getMessage(), e);
                throw e;
            } finally {
                cleanupLockIfNeeded(memoryId);
            }
        }
    }

    private int getNextSequenceNumber(String memoryId) {
        AtomicInteger counter = sequenceCounters.computeIfAbsent(memoryId, k -> {
            Integer dbCount = messageRepo.findMaxSequenceNumByMemoryId(memoryId).orElse(null);
            return new AtomicInteger(dbCount != null ? dbCount : -1);
        });

        return counter.incrementAndGet();
    }

    private ConversationEntity getOrCreateConversation(String memoryId, String fallbackTitle) {
        return conversationRepo.findByMemoryId(memoryId)
                .orElseGet(() -> {
                    ConversationEntity c = new ConversationEntity();
                    c.setMemoryId(memoryId);
                    c.setStatus((short) 1);
                    c.setMessageCount(0);
                    c.setTitle(truncateTitle(fallbackTitle));
                    return c;
                });
    }

    private MessageEntity buildMessageEntity(ConversationEntity conv, String role, String content,
            String thinkingContent, int seqNum) {
        MessageEntity msg = new MessageEntity();
        msg.setConversation(conv);
        msg.setRole(role);
        msg.setContent(content);
        msg.setThinkingContent(thinkingContent);
        msg.setSequenceNum(seqNum);
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }

    private void updateConversationMetadata(ConversationEntity conv, String content) {
        long currentCount = conv.getMessageCount() != null ? conv.getMessageCount() : 0;

        if (currentCount >= Integer.MAX_VALUE - 1) {
            log.warn("⚠️ Message count approaching integer limit for conversation: {}", conv.getMemoryId());
            conv.setMessageCount(Integer.MAX_VALUE);
        } else {
            conv.setMessageCount((int) (currentCount + 1));
        }

        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepo.save(conv);
    }

    private void cleanupLockIfNeeded(String memoryId) {
        if (!sequenceCounters.containsKey(memoryId)) {
            memoryIdLocks.remove(memoryId);
            sequenceCounters.remove(memoryId);
        }
    }

    private String extractLastMessagePreview(ConversationContext ctx) {
        if (ctx.getMessages() == null || ctx.getMessages().isEmpty()) {
            return null;
        }

        var lastEntry = ctx.getMessages().get(ctx.getMessages().size() - 1);
        String content = lastEntry.getContent();
        if (content != null && content.length() > 100) {
            content = content.substring(0, 100);
        }
        return content;
    }

    public ConversationContext loadContext(String memoryId) {
        if (!enabled)
            return ConversationContext.empty(memoryId);

        Level1: {
            if (cacheService != null) {
                byte[] cached = cacheService.getCachedBytes(memoryId);
                if (cached != null && cached.length > 0) {
                    ConversationContext ctx = kryoSerializer.fromBytes(cached);
                    if (ctx != null) {
                        log.debug("Level1 hit: restored from Redis for memoryId={}", memoryId);
                        return ctx;
                    }
                }
            }
        }

        Level2: {
            var convOpt = conversationRepo.findByMemoryId(memoryId);
            if (convOpt.isPresent()) {
                ConversationEntity conv = convOpt.get();
                var snapOpt = snapshotRepo.findByConversation(conv);
                if (snapOpt.isPresent()) {
                    SnapshotEntity snap = snapOpt.get();
                    if (kryoSerializer.validateChecksum(snap.getSnapshotData(), snap.getChecksum())) {
                        ConversationContext ctx = kryoSerializer.fromBytes(snap.getSnapshotData());
                        if (ctx != null) {
                            if (cacheService != null) {
                                cacheService.cacheContext(memoryId, snap.getSnapshotData());
                            }
                            log.info("Level2 hit: restored from PostgreSQL snapshot for memoryId={}", memoryId);
                            return ctx;
                        }
                    } else {
                        log.warn("Level2 checksum mismatch for memoryId={}, falling back to messages", memoryId);
                    }
                }

                List<MessageEntity> msgEntities = messageRepo.findByConversationOrderBySequenceNumAsc(conv);
                if (!msgEntities.isEmpty()) {
                    ConversationContext rebuilt = rebuildFromMessages(memoryId, conv.getModelName(), msgEntities);
                    log.info("Level3 hit: rebuilt from {} messages for memoryId={}", msgEntities.size(), memoryId);
                    saveContext(memoryId, rebuilt);
                    return rebuilt;
                }
            }
        }

        log.info("Level4: no data found for memoryId={}, returning empty context", memoryId);
        return ConversationContext.empty(memoryId);
    }

    public ConversationContext restoreOrCreate(String memoryId) {
        ConversationContext loaded = loadContext(memoryId);
        if (loaded == null || loaded.isEmpty()) {
            return ConversationContext.empty(memoryId);
        }
        return loaded;
    }

    private ConversationContext rebuildFromMessages(String memoryId, String modelName, List<MessageEntity> entities) {
        ConversationContext ctx = new ConversationContext();
        ctx.setMemoryId(memoryId);
        ctx.setModelName(modelName);
        List<com.example.astrastudioopenai.dto.response.MessageEntry> entries = entities.stream()
                .map(e -> {
                    com.example.astrastudioopenai.dto.response.MessageEntry entry = new com.example.astrastudioopenai.dto.response.MessageEntry(
                            e.getRole(), e.getContent(), e.getSequenceNum());
                    entry.setThinkingContent(e.getThinkingContent());
                    entry.setAttachmentsJson(e.getAttachmentsJson());
                    entry.setTimestamp(e.getCreatedAt());
                    return entry;
                })
                .collect(Collectors.toList());
        ctx.setMessages(entries);
        ctx.setVersion(entities.size());
        return ctx;
    }

    private void flushToDbInternal(String memoryId, ConversationContext ctx, byte[] bytes, String checksum) {
        log.info("📝 flushToDbInternal: memoryId={}, messages={}", memoryId, ctx != null ? ctx.getMessageCount() : 0);
        try {
            ConversationEntity conv = conversationRepo.findByMemoryId(memoryId)
                    .orElseGet(() -> {
                        ConversationEntity c = new ConversationEntity();
                        c.setMemoryId(memoryId);
                        c.setStatus((short) 1);
                        c.setMessageCount(ctx.getMessageCount());
                        c.setTitle(
                                ctx.getMessageCount() > 0 ? truncateTitle(ctx.getMessages().get(0).getContent()) : "");
                        return c;
                    });
            conv.setModelName(ctx.getModelName() != null ? ctx.getModelName() : conv.getModelName());
            conv.setMessageCount(ctx.getMessageCount());
            conversationRepo.save(conv);

            SnapshotEntity snapshot = snapshotRepo.findByConversation(conv)
                    .orElseGet(SnapshotEntity::new);
            snapshot.setConversation(conv);
            snapshot.setSnapshotData(bytes);
            snapshot.setVersion(ctx.getVersion());
            snapshot.setChecksum(checksum);
            snapshot.setKvSize(bytes.length);
            snapshotRepo.save(snapshot);

            int seqNum = 0;
            for (com.example.astrastudioopenai.dto.response.MessageEntry entry : ctx.getMessages()) {
                MessageEntity msg = new MessageEntity();
                msg.setConversation(conv);
                msg.setRole(entry.getRole());
                msg.setContent(entry.getContent());
                msg.setThinkingContent(entry.getThinkingContent());
                msg.setAttachmentsJson(entry.getAttachmentsJson());
                msg.setSequenceNum(seqNum++);
                msg.setCreatedAt(entry.getTimestamp() != null ? entry.getTimestamp() : LocalDateTime.now());
                messageRepo.save(msg);
            }

            log.debug("Flushed to DB: memoryId={}, version={}, msgCount={}", memoryId, ctx.getVersion(),
                    ctx.getMessageCount());
        } catch (Exception e) {
            log.error("Failed to flush context to DB for memoryId={}", memoryId, e);
        }
    }

    private static String truncateTitle(String text) {
        if (text == null)
            return "";
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
