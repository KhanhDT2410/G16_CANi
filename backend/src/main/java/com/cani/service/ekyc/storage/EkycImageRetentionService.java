package com.cani.service.ekyc.storage;

import com.cani.entity.EoEkycVerification;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.EkycVerificationStatus;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Chính sách lưu giữ và xóa định kỳ ảnh CCCD / Selfie riêng tư theo trạng thái hồ sơ.
 * Đảm bảo ảnh giấy tờ tùy thân không tồn tại vĩnh viễn trên hệ thống lưu trữ.
 */
@Slf4j
@Service
public class EkycImageRetentionService {

    private final EkycImageStorage imageStorage;
    private final WorkerEkycVerificationRepository workerEkycRepository;
    private final EoEkycVerificationRepository eoEkycRepository;

    private final int approvedRetentionDays;
    private final int rejectedRetentionDays;
    private final int technicalFailureRetentionDays;
    private final int pendingReviewRetentionDays;
    private final boolean cleanupEnabled;

    @Autowired
    public EkycImageRetentionService(
            EkycImageStorage imageStorage,
            WorkerEkycVerificationRepository workerEkycRepository,
            EoEkycVerificationRepository eoEkycRepository,
            @Value("${cani.ekyc.retention.approved-image-days:30}") int approvedRetentionDays,
            @Value("${cani.ekyc.retention.rejected-image-days:7}") int rejectedRetentionDays,
            @Value("${cani.ekyc.retention.technical-failure-image-days:3}") int technicalFailureRetentionDays,
            @Value("${cani.ekyc.retention.pending-review-image-days:30}") int pendingReviewRetentionDays,
            @Value("${cani.ekyc.retention.cleanup-enabled:true}") boolean cleanupEnabled
    ) {
        this.imageStorage = imageStorage;
        this.workerEkycRepository = workerEkycRepository;
        this.eoEkycRepository = eoEkycRepository;
        this.approvedRetentionDays = Math.max(1, approvedRetentionDays);
        this.rejectedRetentionDays = Math.max(1, rejectedRetentionDays);
        this.technicalFailureRetentionDays = Math.max(1, technicalFailureRetentionDays);
        this.pendingReviewRetentionDays = Math.max(1, pendingReviewRetentionDays);
        this.cleanupEnabled = cleanupEnabled;
    }

    public Instant calculateRetentionDeadline(EkycVerificationStatus status) {
        Instant now = Instant.now();
        if (status == null) {
            return now.plus(pendingReviewRetentionDays, ChronoUnit.DAYS);
        }
        return switch (status) {
            case APPROVED -> now.plus(approvedRetentionDays, ChronoUnit.DAYS);
            case REJECTED -> now.plus(rejectedRetentionDays, ChronoUnit.DAYS);
            case TECHNICAL_FAILURE -> now.plus(technicalFailureRetentionDays, ChronoUnit.DAYS);
            case PENDING, PROCESSING, PENDING_REVIEW -> now.plus(pendingReviewRetentionDays, ChronoUnit.DAYS);
        };
    }

    public void deleteStoredImagesQuietly(String frontKey, String backKey, String selfieKey) {
        if (imageStorage == null) {
            return;
        }
        imageStorage.deleteQuietly(frontKey);
        imageStorage.deleteQuietly(backKey);
        imageStorage.deleteQuietly(selfieKey);
    }

    /**
     * Tác vụ định kỳ xóa các tệp ảnh CCCD / Selfie đã quá thời hạn lưu giữ và cập nhật images_purged_at.
     */
    @Scheduled(cron = "${cani.ekyc.retention.cleanup-cron:0 30 2 * * *}")
    @Transactional
    public int purgeExpiredImages() {
        if (!cleanupEnabled) {
            return 0;
        }
        Instant now = Instant.now();
        int purgedCount = 0;

        List<WorkerEkycVerification> expiredWorkers =
                workerEkycRepository.findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(now);
        for (WorkerEkycVerification record : expiredWorkers) {
            deleteStoredImagesQuietly(record.getFrontImageUrl(), record.getBackImageUrl(), record.getSelfieImageUrl());
            record.setFrontImageUrl(null);
            record.setBackImageUrl(null);
            record.setSelfieImageUrl(null);
            record.setImagesPurgedAt(now);
            workerEkycRepository.save(record);
            purgedCount++;
        }

        List<EoEkycVerification> expiredEos =
                eoEkycRepository.findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(now);
        for (EoEkycVerification record : expiredEos) {
            deleteStoredImagesQuietly(record.getFrontImageUrl(), record.getBackImageUrl(), record.getSelfieImageUrl());
            record.setFrontImageUrl(null);
            record.setBackImageUrl(null);
            record.setSelfieImageUrl(null);
            record.setImagesPurgedAt(now);
            eoEkycRepository.save(record);
            purgedCount++;
        }

        if (purgedCount > 0) {
            log.info("Đã dọn dẹp ảnh eKYC hết hạn lưu giữ cho {} hồ sơ.", purgedCount);
        }
        return purgedCount;
    }
}
