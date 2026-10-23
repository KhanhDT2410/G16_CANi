package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity lưu trữ mã xác thực OTP gửi qua Email khi người dùng đăng ký hoặc khôi phục mật khẩu.
 */
@Entity
@Table(name = "email_otp_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "otp_code", length = 10, nullable = false)
    private String otpCode;

    @Column(name = "otp_type", length = 50, nullable = false)
    @Builder.Default
    private String otpType = "REGISTER"; // REGISTER, RESET_PASSWORD

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_used")
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "attempts")
    @Builder.Default
    private Integer attempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
