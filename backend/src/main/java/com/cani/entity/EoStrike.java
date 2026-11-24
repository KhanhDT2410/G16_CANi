package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eo_strike")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EoStrike {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID strikeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eo_id", nullable = false)
    private EventOrganizer eo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false, unique = true)
    private Dispute dispute;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
