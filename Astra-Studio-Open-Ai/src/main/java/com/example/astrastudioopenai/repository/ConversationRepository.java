package com.example.astrastudioopenai.repository;

import com.example.astrastudioopenai.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    Optional<ConversationEntity> findByMemoryId(String memoryId);
    boolean existsByMemoryId(String memoryId);
}
