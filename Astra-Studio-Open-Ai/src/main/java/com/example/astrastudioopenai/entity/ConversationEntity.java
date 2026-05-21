package com.example.astrastudioopenai.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_id", nullable = false, unique = true)
    private String memoryId;

    @Column(name = "title")
    private String title;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "message_count")
    private Integer messageCount;

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "status")
    private Short status;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
