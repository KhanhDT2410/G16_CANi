package com.cani.service.ekyc.storage;

import com.cani.dto.ekyc.EkycLegacyRetentionInventoryDto;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chính sách vòng đời & xóa ảnh CCCD / Selfie riêng tư theo trạng thái hồ sơ:
 * - APPROVED / REJECTED: mặc định (0 ngày) xóa ngay sau khi commit kết quả trừ khi legal_hold = true.
 * - PENDING_REVIEW: giữ tạm có thời hạn (mặc định 14 ngày, luôn > 0) để Admin / CSKH duyệt tay.
 * - TECHNICAL_FAILURE: giữ tạm có thời hạn (mặc định 3 ngày, luôn > 0) phục vụ đối soát sự cố.
 * - Tuyệt đối không biến thời hạn 0 thành NULL giữ vô hạn.
 * - Chỉ ghi images_purged_at sau khi xóa file thành công; nếu lỗi xóa thì giữ nguyên key để retry.
 */
@Slf4j
@Service
public class EkycImageRetentionService {

    public static final int DEFAULT_TECHNICAL_FAILURE_DAYS = 3;
    public static final int DEFAULT_PENDING_REVIEW_DAYS = 14;
    public static final int DEFAULT_IN_FLIGHT_DAYS = 1;

    private final EkycImageStorage imageStorage;
    private final WorkerEkycVerificationRepository workerEkycRepository;
    private final EoEkycVerificationRepository eoEkycRepository;

    private final int approvedRetentionDays;
    private final int rejectedRetentionDays;
    private final int technicalFailureRetentionDays;
    private final int pendingReviewRetentionDays;
    private final boolean cleanupEnabled;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired
    public EkycImageRetentionService(
            EkycImageStorage imageStorage,
            WorkerEkycVerificationRepository workerEkycRepository,
            EoEkycVerificationRepository eoEkycRepository,
            @Value("${cani.ekyc.retention.approved-image-days:0}") int approvedRetentionDays,
            @Value("${cani.ekyc.retention.rejected-image-days:0}") int rejectedRetentionDays,
            @Value("${cani.ekyc.retention.technical-failure-image-days:3}") int technicalFailureRetentionDays,
            @Value("${cani.ekyc.retention.pending-review-image-days:14}") int pendingReviewRetentionDays,
            @Value("${cani.ekyc.retention.cleanup-enabled:true}") boolean cleanupEnabled
    ) {
        this.imageStorage = imageStorage;
        this.workerEkycRepository = workerEkycRepository;
        this.eoEkycRepository = eoEkycRepository;
        this.approvedRetentionDays = Math.max(0, approvedRetentionDays);
        this.rejectedRetentionDays = Math.max(0, rejectedRetentionDays);
        this.technicalFailureRetentionDays = technicalFailureRetentionDays > 0
                ? technicalFailureRetentionDays
                : DEFAULT_TECHNICAL_FAILURE_DAYS;
        this.pendingReviewRetentionDays = pendingReviewRetentionDays > 0
                ? pendingReviewRetentionDays
                : DEFAULT_PENDING_REVIEW_DAYS;
        this.cleanupEnabled = cleanupEnabled;
    }

    /**
     * Tính hạn lưu giữ ảnh theo trạng thái nghiệp vụ.
     * Không bao giờ trả về NULL đối với hồ sơ mới tạo/cập nhật:
     * - APPROVED / REJECTED với retentionDays = 0 -> Instant.now() (đến hạn xóa ngay sau commit).
     * - PENDING_REVIEW / TECHNICAL_FAILURE / PROCESSING -> hạn tạm thời dương (> 0 ngày).
     */
    public Instant calculateRetentionDeadline(EkycVerificationStatus status) {
        Instant now = Instant.now();
        if (status == null) {
            return now.plus(DEFAULT_IN_FLIGHT_DAYS, ChronoUnit.DAYS);
        }
        return switch (status) {
            case APPROVED -> approvedRetentionDays > 0
                    ? now.plus(approvedRetentionDays, ChronoUnit.DAYS)
                    : now;
            case REJECTED -> rejectedRetentionDays > 0
                    ? now.plus(rejectedRetentionDays, ChronoUnit.DAYS)
                    : now;
            case TECHNICAL_FAILURE -> now.plus(technicalFailureRetentionDays, ChronoUnit.DAYS);
            case PENDING_REVIEW -> now.plus(pendingReviewRetentionDays, ChronoUnit.DAYS);
            case PENDING, PROCESSING -> now.plus(DEFAULT_IN_FLIGHT_DAYS, ChronoUnit.DAYS);
        };
    }

    public boolean isImmediatePurgeStatus(EkycVerificationStatus status) {
        return (status == EkycVerificationStatus.APPROVED && approvedRetentionDays == 0)
                || (status == EkycVerificationStatus.REJECTED && rejectedRetentionDays == 0);
    }

    /**
     * Lên lịch xóa ảnh ngay sau khi transaction hiện tại commit (nếu trạng thái thuộc nhóm xóa ngay và không bị legalHold).
     */
    public void scheduleImmediatePurgeIfEligible(String targetType, UUID verificationId, EkycVerificationStatus status) {
        if (verificationId == null || !isImmediatePurgeStatus(status)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            purgeSingleRecordSafely(targetType, verificationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                purgeSingleRecordSafely(targetType, verificationId);
            }
        });
    }

    public boolean purgeSingleRecordSafely(String targetType, UUID verificationId) {
        if (verificationId == null) {
            return false;
        }
        if (transactionManager != null) {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Boolean result = tx.execute(status -> executeLockedPurge(targetType, verificationId));
            return Boolean.TRUE.equals(result);
        }
        return executeLockedPurge(targetType, verificationId);
    }

    private boolean executeLockedPurge(String targetType, UUID verificationId) {
        Instant now = Instant.now();
        if ("worker".equals(targetType)) {
            WorkerEkycVerification record = workerEkycRepository.findByIdForUpdate(verificationId)
                    .or(() -> workerEkycRepository.findById(verificationId))
                    .orElse(null);
            return record != null && tryPurgeWorkerRecord(record, now);
        }
        EoEkycVerification record = eoEkycRepository.findByIdForUpdate(verificationId)
                .or(() -> eoEkycRepository.findById(verificationId))
                .orElse(null);
        return record != null && tryPurgeEoRecord(record, now);
    }

    public void deleteAfterCommit(String front, String back, String selfie) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStoredImagesQuietly(front, back, selfie);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override public void afterCommit() { deleteStoredImagesQuietly(front, back, selfie); }
                });
    }

    public void deleteAfterRollback(String front, String back, String selfie) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) deleteStoredImagesQuietly(front, back, selfie);
                        }
                    });
        }
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
     * Mỗi hồ sơ được khóa PESSIMISTIC_WRITE độc lập để tránh xung đột với Admin đang duyệt hoặc bật legal_hold.
     */
    @Scheduled(cron = "${cani.ekyc.retention.cleanup-cron:0 30 2 * * *}")
    public int purgeExpiredImages() {
        if (!cleanupEnabled) {
            return 0;
        }
        Instant now = Instant.now();
        int purgedCount = 0;

        List<WorkerEkycVerification> expiredWorkers =
                workerEkycRepository.findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(now);
        if (expiredWorkers != null) {
            for (WorkerEkycVerification candidate : expiredWorkers) {
                if (candidate.getId() != null && transactionManager != null) {
                    if (purgeSingleRecordSafely("worker", candidate.getId())) {
                        purgedCount++;
                    }
                } else if (tryPurgeWorkerRecord(candidate, now)) {
                    purgedCount++;
                }
            }
        }

        List<EoEkycVerification> expiredEos =
                eoEkycRepository.findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(now);
        if (expiredEos != null) {
            for (EoEkycVerification candidate : expiredEos) {
                if (candidate.getId() != null && transactionManager != null) {
                    if (purgeSingleRecordSafely("eo", candidate.getId())) {
                        purgedCount++;
                    }
                } else if (tryPurgeEoRecord(candidate, now)) {
                    purgedCount++;
                }
            }
        }

        if (purgedCount > 0) {
            log.info("Đã dọn dẹp ảnh eKYC hết hạn lưu giữ cho {} hồ sơ.", purgedCount);
        }
        return purgedCount;
    }

    private boolean tryPurgeWorkerRecord(WorkerEkycVerification record, Instant now) {
        if (record == null || record.isLegalHold()
                || record.getImagesPurgedAt() != null
                || EkycVerificationStatus.PROCESSING.name().equals(record.getStatus())
                || record.getImageRetentionUntil() == null
                || record.getImageRetentionUntil().isAfter(now)) {
            return false;
        }
        if (!deleteAllRecordImages(record.getFrontImageUrl(), record.getBackImageUrl(), record.getSelfieImageUrl())) {
            return false;
        }
        record.setFrontImageUrl(null);
        record.setBackImageUrl(null);
        record.setSelfieImageUrl(null);
        record.setImagesPurgedAt(now);
        workerEkycRepository.save(record);
        return true;
    }

    private boolean tryPurgeEoRecord(EoEkycVerification record, Instant now) {
        if (record == null || record.isLegalHold()
                || record.getImagesPurgedAt() != null
                || EkycVerificationStatus.PROCESSING.name().equals(record.getStatus())
                || record.getImageRetentionUntil() == null
                || record.getImageRetentionUntil().isAfter(now)) {
            return false;
        }
        if (!deleteAllRecordImages(record.getFrontImageUrl(), record.getBackImageUrl(), record.getSelfieImageUrl())) {
            return false;
        }
        record.setFrontImageUrl(null);
        record.setBackImageUrl(null);
        record.setSelfieImageUrl(null);
        record.setImagesPurgedAt(now);
        eoEkycRepository.save(record);
        return true;
    }

    private boolean deleteAllRecordImages(String front, String back, String selfie) {
        if (imageStorage == null) {
            return false;
        }
        return imageStorage.deleteIfPresent(front)
                && imageStorage.deleteIfPresent(back)
                && imageStorage.deleteIfPresent(selfie);
    }

    /**
     * Truy vấn kiểm kê CHỈ trả số đếm các hồ sơ cũ có image_retention_until IS NULL và chưa purge.
     * Tuyệt đối không gán hạn tự động hay xóa tệp thật.
     */
    @Transactional(readOnly = true)
    public EkycLegacyRetentionInventoryDto getLegacyNullRetentionInventory() {
        long workerTotal = workerEkycRepository.countByImagesPurgedAtIsNullAndImageRetentionUntilIsNull();
        long eoTotal = eoEkycRepository.countByImagesPurgedAtIsNullAndImageRetentionUntilIsNull();
        Map<String, Long> workerByStatus = toStatusCountMap(workerEkycRepository.countLegacyNullRetentionByStatus());
        Map<String, Long> eoByStatus = toStatusCountMap(eoEkycRepository.countLegacyNullRetentionByStatus());
        return EkycLegacyRetentionInventoryDto.builder()
                .workerLegacyNullRetentionCount(workerTotal)
                .eoLegacyNullRetentionCount(eoTotal)
                .totalLegacyNullRetentionCount(workerTotal + eoTotal)
                .workerByStatus(workerByStatus)
                .eoByStatus(eoByStatus)
                .build();
    }

    private Map<String, Long> toStatusCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] instanceof Number num) {
                    map.put(row[0].toString(), num.longValue());
                }
            }
        }
        return map;
    }
}
