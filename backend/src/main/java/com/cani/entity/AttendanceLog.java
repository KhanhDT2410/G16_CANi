package com.cani.entity;

import com.cani.entity.enums.AttendanceStatus;
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
@Table(name = "attendance_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID attendanceId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false, unique = true)
    private ShiftAssignment shiftAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "checkin_time")
    private Instant checkinTime;

    @Column(name = "checkin_qr_hash", length = 255)
    private String checkinQrHash;

    @Column(name = "checkin_lat")
    private Double checkinLat;

    @Column(name = "checkin_lng")
    private Double checkinLng;

    @Column(name = "checkout_time")
    private Instant checkoutTime;

    @Column(name = "checkout_qr_hash", length = 255)
    private String checkoutQrHash;

    @Column(name = "hours_worked", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal hoursWorked = BigDecimal.ZERO;

    @Column(name = "is_on_time")
    @Builder.Default
    private Boolean isOnTime = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "attendance_status")
    @Builder.Default
    private AttendanceStatus attendanceStatus = AttendanceStatus.NOT_CHECKED_IN;

    @Column(name = "qr_generated_at")
    private Instant qrGeneratedAt;

    @Column(name = "qr_expires_at")
    private Instant qrExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
