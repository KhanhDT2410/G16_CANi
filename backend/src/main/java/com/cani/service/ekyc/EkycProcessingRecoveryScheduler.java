package com.cani.service.ekyc;

import com.cani.service.EventOrganizerEkycService;
import com.cani.service.WorkerEkycService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ định kỳ thu hồi các phiên eKYC bị kẹt ở trạng thái PROCESSING quá thời gian chờ
 * (ví dụ sự cố timeout/restart khi gọi VNPT), chuyển về TECHNICAL_FAILURE để người dùng nộp lại.
 */
@Component
@RequiredArgsConstructor
public class EkycProcessingRecoveryScheduler {

    private final WorkerEkycService workerEkycService;
    private final EventOrganizerEkycService eventOrganizerEkycService;

    @Scheduled(cron = "${cani.ekyc.processing-recovery-cron:0 */1 * * * *}")
    public void recoverStaleProcessingEkyc() {
        workerEkycService.recoverStaleProcessingRecords();
        eventOrganizerEkycService.recoverStaleProcessingRecords();
    }
}
