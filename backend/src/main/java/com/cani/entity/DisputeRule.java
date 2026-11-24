package com.cani.entity;

import com.cani.entity.enums.DisputeOutcome;
import com.cani.entity.enums.DisputeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispute_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "behavior", nullable = false, columnDefinition = "TEXT")
    private String behavior;

    @Column(name = "violating_party", nullable = false, length = 20)
    private String violatingParty;

    @Column(name = "sanction_form", nullable = false, columnDefinition = "TEXT")
    private String sanctionForm;

    @Column(name = "specific_penalty", nullable = false, columnDefinition = "TEXT")
    private String specificPenalty;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private DisputeType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fault_outcome", nullable = false, length = 50)
    private DisputeOutcome faultOutcome;

    @Column(name = "repeat_window_days")
    private Integer repeatWindowDays;

    @Column(name = "repeat_index", nullable = false)
    @Builder.Default
    private Integer repeatIndex = 1;

    @Column(name = "sanction_text", nullable = false, columnDefinition = "TEXT")
    private String sanctionText;

    @Column(name = "admin_action_text", columnDefinition = "TEXT")
    private String adminActionText;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
