package com.cani.entity;

import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.DisputeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dispute_id", updatable = false, nullable = false)
    private UUID disputeId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "complainant_id", nullable = false)
    private UUID complainantId;

    @Column(name = "respondent_id", nullable = false)
    private UUID respondentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private DisputeType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence", columnDefinition = "jsonb")
    private String evidence;

    @Column(name = "messages", columnDefinition = "jsonb")
    private String messages;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.SUBMITTED;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
