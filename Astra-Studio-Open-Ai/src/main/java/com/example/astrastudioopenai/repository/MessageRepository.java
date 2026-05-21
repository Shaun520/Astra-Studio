package com.example.astrastudioopenai.repository;

import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByConversationOrderBySequenceNumAsc(ConversationEntity conversation);

    List<MessageEntity> findByConversationIdOrderBySequenceNumAsc(Long conversationId);

    void deleteByConversation(ConversationEntity conversation);

    Page<MessageEntity> findByConversationOrderBySequenceNumAsc(ConversationEntity conversation, Pageable pageable);

    Page<MessageEntity> findByConversationAndRoleOrderBySequenceNumAsc(ConversationEntity conversation, String role,
            Pageable pageable);

    @Query("SELECT MAX(m.sequenceNum) FROM MessageEntity m WHERE m.conversation.memoryId = :memoryId")
    Optional<Integer> findMaxSequenceNumByMemoryId(String memoryId);
}
