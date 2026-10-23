package com.cani.repository;

import com.cani.entity.EmailOtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOtpVerificationRepository extends JpaRepository<EmailOtpVerification, UUID> {

    /**
     * Tìm mã OTP mới nhất theo email, loại OTP và chưa sử dụng
     */
    Optional<EmailOtpVerification> findTopByEmailIgnoreCaseAndOtpTypeAndIsUsedFalseOrderByCreatedAtDesc(
            String email, String otpType
    );
}
