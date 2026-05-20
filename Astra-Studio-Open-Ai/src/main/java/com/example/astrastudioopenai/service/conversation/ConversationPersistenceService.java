package com.example.astrastudioopenai.service.conversation;

import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.MessageEntity;
import com.example.astrastudioopenai.entity.SnapshotEntity;
import com.example.astrastudioopenai.repository.ConversationRepository;
import com.example.astrastudioopenai.repository.MessageRepository;
import com.example.astrastudioopenai.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ConversationPersistenceService.class);

    private final KryoSerializer kryoSerializer;
    private final ConversationCacheService cacheService;
    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final SnapshotRepository snapshotRepo;

    @Value("${conversation.persistence.enabled:false}")
    private boolean enabled;

    @Value("${conversation.persistence.async-flush:true}")
    private boolean asyncFlush;

    public ConversationPersistenceService(KryoSerializer kryoSerializer,
                                           ConversationCacheService cacheService,
                                           ConversationRepository conversationRepo,
                                           MessageRepository messageRepo,
                                           SnapshotRepository snapshotRepo) {
        this.kryoSerializer = kryoSerializer;
        this.cacheService = cacheService;
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.snapshotRepo = snapshotRepo;
    }

    public void saveContext(String memoryId, ConversationContext ctx) {
        if (!enabled || ctx == null) return;

        byte[] bytes = kryoSerializer.toBytes(ctx);
        String checksum = kryoSerializer.computeChecksum(bytes);

        ctx.setChecksum(checksum);
        ctx.setKvSize(bytes.length);

        cacheService.cacheContext(memoryId, bytes);

        if (asyncFlush) {
            flushToDbAsync(memoryId, ctx, bytes, checksum);
        } else {
            flushToDbSync(memoryId, ctx, bytes, checksum);
        }
    }

    public ConversationContext loadContext(String memoryId) {
        if (!enabled) return ConversationContext.empty(memoryId);

        Level1: {
            byte[] cached = cacheService.getCachedBytes(memoryId);
            if (cached != null && cached.length > 0) {
                ConversationContext ctx = kryoSerializer.fromBytes(cached);
                if (ctx != null) {
                    log.debug("Level1 hit: restored from Redis for memoryId={}", memoryId);
                    return ctx;
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
                            cacheService.cacheContext(memoryId, snap.getSnapshotData());
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
                    com.example.astrastudioopenai.dto.response.MessageEntry entry = new com.example.astrastudioopenai.dto.response.MessageEntry(e.getRole(), e.getContent(), e.getSequenceNum());
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

    @Async("persistenceExecutor")
    protected void flushToDbAsync(String memoryId, ConversationContext ctx, byte[] bytes, String checksum) {
        flushToDbInternal(memoryId, ctx, bytes, checksum);
    }

    @Transactional
    protected void flushToDbSync(String memoryId, ConversationContext ctx, byte[] bytes, String checksum) {
        flushToDbInternal(memoryId, ctx, bytes, checksum);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void flushToDbInternal(String memoryId, ConversationContext ctx, byte[] bytes, String checksum) {
        try {
            ConversationEntity conv = conversationRepo.findByMemoryId(memoryId)
                    .orElseGet(() -> {
                        ConversationEntity c = new ConversationEntity();
                        c.setMemoryId(memoryId);
                        c.setStatus((short) 1);
                        c.setMessageCount(ctx.getMessageCount());
                        c.setTitle(ctx.getMessageCount() > 0 ? truncateTitle(ctx.getMessages().get(0).getContent()) : "");
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

            log.debug("Flushed to DB: memoryId={}, version={}, msgCount={}", memoryId, ctx.getVersion(), ctx.getMessageCount());
        } catch (Exception e) {
            log.error("Failed to flush context to DB for memoryId={}", memoryId, e);
        }
    }

    private static String truncateTitle(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
