package com.cani.entity;

import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wage_matrix")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WageMatrix {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "matrix_id", updatable = false, nullable = false)
    private UUID matrixId;

    @Column(name = "city", nullable = false, length = 50)
    private String city;

    @Column(name = "job_type", nullable = false, length = 100)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false, length = 50)
    private TimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "lead_time", nullable = false, length = 50)
    private LeadTime leadTime;

    @Column(name = "wage_per_hour", nullable = false, precision = 12, scale = 2)
    private BigDecimal wagePerHour;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
