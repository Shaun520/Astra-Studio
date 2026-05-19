package com.example.astrastudioopenai.repository;

import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByConversationOrderBySequenceNumAsc(ConversationEntity conversation);
    List<MessageEntity> findByConversationIdOrderBySequenceNumAsc(Long conversationId);
    void deleteByConversation(ConversationEntity conversation);
}
