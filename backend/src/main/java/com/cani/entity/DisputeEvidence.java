package com.cani.entity;

import com.cani.entity.enums.DisputeEvidenceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Bằng chứng của một bên liên quan trong tranh chấp (SRS UC-98, BR-56: giữ bằng chứng 36 tháng).
 */
@Entity
@Table(name = "dispute_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID evidenceId;

    @Column(name = "dispute_id", nullable = false)
    private UUID disputeId;

    /**
     * ID (users.id) của bên nộp bằng chứng (complainant hoặc respondent).
     */
    @Column(name = "party", nullable = false)
    private UUID party;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "file_type", length = 50)
    private DisputeEvidenceType fileType;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "label", length = 255)
    private String label;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
