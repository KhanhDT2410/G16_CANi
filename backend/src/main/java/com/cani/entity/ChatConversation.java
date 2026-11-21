package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Phòng hội thoại trao đổi trực tiếp hoặc theo ca làm việc (EO - Worker).
 */
@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private String type = "DIRECT"; // 'DIRECT' | 'SHIFT'

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "last_message_at")
    @Builder.Default
    private Instant lastMessageAt = Instant.now();

    @Column(name = "last_message_preview", length = 500)
    private String lastMessagePreview;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
