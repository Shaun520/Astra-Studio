package com.example.astrastudioopenai.scheduled;

import com.example.astrastudioopenai.repository.ConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class ConversationCleanupTask {

    @Autowired
    private ConversationRepository conversationRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupSoftDeletedConversations() {
        log.info("🧹 Starting cleanup of soft-deleted conversations older than 30 days");

        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(30);

            log.info("Deleting conversations with deleted_at before: {}", threshold);

            conversationRepository.hardDeleteOlderThan(threshold);

            log.info("✅ Cleanup completed successfully");
        } catch (Exception e) {
            log.error("❌ Failed to cleanup soft-deleted conversations", e);
        }
    }
}
