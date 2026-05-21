package com.example.astrastudioopenai.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversation_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id"}))
public class SnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false, unique = true)
    private ConversationEntity conversation;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "bytea")
    private byte[] snapshotData;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "kv_size")
    private Integer kvSize;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
