package com.example.astrastudioopenai.repository;

import com.example.astrastudioopenai.entity.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {
}
