package com.cani.repository;

import com.cani.entity.EkycConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EkycConsentRecordRepository extends JpaRepository<EkycConsentRecord, UUID> {
    Optional<EkycConsentRecord> findTopByUserIdAndNoticeVersionOrderByConsentedAtDesc(UUID userId, String noticeVersion);
}
