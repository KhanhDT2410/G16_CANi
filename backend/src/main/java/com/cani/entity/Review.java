package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID reviewId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private UUID revieweeId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "criteria_work_completion")
    private Integer criteriaWorkCompletion;

    @Column(name = "criteria_punctuality")
    private Integer criteriaPunctuality;

    @Column(name = "criteria_professionalism")
    private Integer criteriaProfessionalism;

    @Column(name = "criteria_venue_rules")
    private Integer criteriaVenueRules;

    @Column(name = "criteria_teamwork")
    private Integer criteriaTeamwork;

    @Column(name = "criteria_shift_clarity")
    private Integer criteriaShiftClarity;

    @Column(name = "criteria_timing")
    private Integer criteriaTiming;

    @Column(name = "criteria_work_environment")
    private Integer criteriaWorkEnvironment;

    @Column(name = "criteria_support")
    private Integer criteriaSupport;

    @Column(name = "criteria_payment_timing")
    private Integer criteriaPaymentTiming;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "is_auto_closed")
    @Builder.Default
    private Boolean isAutoClosed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
