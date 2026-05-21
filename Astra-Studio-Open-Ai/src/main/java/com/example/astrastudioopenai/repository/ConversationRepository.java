package com.example.astrastudioopenai.repository;

import com.example.astrastudioopenai.entity.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    Optional<ConversationEntity> findByMemoryId(String memoryId);
    boolean existsByMemoryId(String memoryId);

    Page<ConversationEntity> findByStatusNotOrderByUpdatedAtDesc(Short status, Pageable pageable);
    Page<ConversationEntity> findByStatusNotAndTitleContainingIgnoreCase(Short status, String keyword, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ConversationEntity c WHERE c.deletedAt < :threshold")
    void hardDeleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
