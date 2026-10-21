package com.cani.entity;

import com.cani.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID paymentId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eo_id", nullable = false)
    private EventOrganizer eventOrganizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "agreed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal agreedAmount;

    @Column(name = "declared_amount", precision = 15, scale = 2)
    private BigDecimal declaredAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.NOT_DECLARED;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "declared_at")
    private Instant declaredAt;

    @Column(name = "external_ref", length = 255)
    private String externalRef;

    @Column(name = "payment_proof_url", columnDefinition = "TEXT")
    private String paymentProofUrl;

    @Column(name = "worker_confirmed_at")
    private Instant workerConfirmedAt;

    @Column(name = "worker_confirm_status", length = 50)
    private String workerConfirmStatus;

    @Column(name = "worker_comment", columnDefinition = "TEXT")
    private String workerComment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
