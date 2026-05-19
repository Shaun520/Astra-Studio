package com.example.astrastudioopenai.repository;

import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.SnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {
    Optional<SnapshotEntity> findByConversation(ConversationEntity conversation);
    Optional<SnapshotEntity> findByConversationId(Long conversationId);
    boolean existsByConversation(ConversationEntity conversation);
}
