package com.example.astrastudioopenai.service.conversation;

import com.example.astrastudioopenai.dto.response.ConversationDTO;
import com.example.astrastudioopenai.dto.response.MessageDTO;
import com.example.astrastudioopenai.dto.response.PageResult;
import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.MessageEntity;
import com.example.astrastudioopenai.exception.EntityExistsException;
import com.example.astrastudioopenai.exception.NotFoundException;
import com.example.astrastudioopenai.repository.ConversationRepository;
import com.example.astrastudioopenai.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationQueryService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public boolean conversationExists(String memoryId) {
        return conversationRepository.existsByMemoryId(memoryId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConversation(String memoryId) {
        ConversationEntity conv = findByMemoryIdOrThrow(memoryId);
        ConversationDTO dto = ConversationDTO.fromEntity(conv);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", dto.getId());
        result.put("memoryId", dto.getMemoryId());
        result.put("title", dto.getTitle());
        result.put("modelName", dto.getModelName());
        result.put("messageCount", dto.getMessageCount());
        result.put("status", dto.getStatus());
        result.put("updatedAt", dto.getUpdatedAt());

        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<ConversationEntity> listConversations(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ConversationEntity> result;

        if (StringUtils.hasText(keyword)) {
            result = conversationRepository.findByStatusNotAndTitleContainingIgnoreCase(
                    (short) -1, keyword, pageable);
        } else {
            result = conversationRepository.findByStatusNotOrderByUpdatedAtDesc((short) -1, pageable);
        }

        return PageResult.fromSpringPage(result);
    }

    @Transactional
    public ConversationEntity createConversation(String memoryId, String modelName) {
        log.info("Creating new conversation: memoryId={}, modelName={}", memoryId, modelName);

        if (conversationRepository.existsByMemoryId(memoryId)) {
            throw new EntityExistsException("Conversation already exists: " + memoryId);
        }

        ConversationEntity conv = new ConversationEntity();
        conv.setMemoryId(memoryId);
        conv.setTitle("新对话");
        conv.setModelName(modelName);
        conv.setStatus((short) 1);
        conv.setMessageCount(0);
        conv.setLastMessagePreview("");
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());

        ConversationEntity saved = conversationRepository.save(conv);
        log.info("Created conversation successfully: id={}, memoryId={}", saved.getId(), saved.getMemoryId());
        return saved;
    }

    @Transactional
    public void updateTitle(String memoryId, String title) {
        log.info("Updating title for conversation: memoryId={}", memoryId);

        ConversationEntity conv = findByMemoryIdOrThrow(memoryId);

        if (title.length() > 255) {
            title = title.substring(0, 255);
        }

        conv.setTitle(title);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        log.info("Updated title for conversation: memoryId={}, title={}", memoryId, title);
    }

    @Transactional
    public void softDelete(String memoryId) {
        log.info("Soft deleting conversation: memoryId={}", memoryId);

        ConversationEntity conv = findByMemoryIdOrThrow(memoryId);

        if (conv.getStatus() == -1) {
            log.info("Conversation already soft deleted: memoryId={}", memoryId);
            return;
        }

        conv.setStatus((short) -1);
        conv.setDeletedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        log.info("Soft deleted conversation: memoryId={}", memoryId);
    }

    @Transactional(readOnly = true)
    public PageResult<MessageDTO> getMessages(String memoryId, int page, int size, String role) {
        log.debug("Getting messages for conversation: memoryId={}, page={}, size={}, role={}",
                memoryId, page, size, role);

        ConversationEntity conv = findByMemoryIdOrThrow(memoryId);
        Pageable pageable = PageRequest.of(page, size);
        Page<MessageEntity> result;

        if (StringUtils.hasText(role)) {
            result = messageRepository.findByConversationAndRoleOrderBySequenceNumAsc(conv, role, pageable);
        } else {
            result = messageRepository.findByConversationOrderBySequenceNumAsc(conv, pageable);
        }

        return PageResult.fromMessagePage(result);
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public void incrementMessageCount(String memoryId) {
        ConversationEntity conv = findByMemoryIdOrThrow(memoryId);

        Integer currentCount = conv.getMessageCount() != null ? conv.getMessageCount() : 0;
        conv.setMessageCount(currentCount + 1);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    @Recover
    public void recoverIncrement(OptimisticLockingFailureException e, String memoryId) {
        log.error("Failed to increment message count after 3 retries for memoryId: {}", memoryId, e);
        throw new RuntimeException("Unable to update conversation due to high concurrency: " + memoryId, e);
    }

    @Transactional
    public void updateLastMessagePreview(String memoryId, String lastMessagePreview) {
        ConversationEntity conv = findByMemoryIdOrThrow(memoryId);

        if (lastMessagePreview != null && lastMessagePreview.length() > 100) {
            lastMessagePreview = lastMessagePreview.substring(0, 100);
        }

        conv.setLastMessagePreview(lastMessagePreview != null ? lastMessagePreview : "");
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    private ConversationEntity findByMemoryIdOrThrow(String memoryId) {
        return conversationRepository.findByMemoryId(memoryId)
                .orElseThrow(() -> new NotFoundException("Conversation not found: " + memoryId));
    }
}
