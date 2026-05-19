package com.example.astrastudioopenai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversation_messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @Column(name = "thinking_content", columnDefinition = "MEDIUMTEXT")
    private String thinkingContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments", columnDefinition = "JSONB")
    private String attachmentsJson;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
