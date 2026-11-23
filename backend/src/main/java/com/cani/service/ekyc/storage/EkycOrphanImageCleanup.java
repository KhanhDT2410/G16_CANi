package com.cani.service.ekyc.storage;

import com.cani.entity.enums.EkycVerificationStatus;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class EkycOrphanImageCleanup {
    private final LocalPrivateEkycImageStorage storage;
    private final WorkerEkycVerificationRepository workers;
    private final EoEkycVerificationRepository organizers;
    private final int graceHours;

    public EkycOrphanImageCleanup(LocalPrivateEkycImageStorage storage,
            WorkerEkycVerificationRepository workers, EoEkycVerificationRepository organizers,
            @Value("${cani.ekyc.retention.orphan-grace-hours:24}") int graceHours) {
        this.storage = storage;
        this.workers = workers;
        this.organizers = organizers;
        this.graceHours = graceHours;
    }

    @Scheduled(cron = "${cani.ekyc.retention.orphan-cleanup-cron:0 45 2 * * *}")
    public int purgeOrphans() {
        if (graceHours <= 0) return 0;
        Instant cutoff = Instant.now().minus(graceHours, ChronoUnit.HOURS);
        AtomicInteger removed = new AtomicInteger();
        try {
            storage.visitAgedObjects(cutoff, key -> {
                if (canDelete(key) && storage.deleteIfPresent(key)) {
                    removed.incrementAndGet();
                }
            });
        } catch (IOException | UncheckedIOException e) {
            log.warn("Private eKYC orphan scan failed [{}]", e.getClass().getSimpleName());
        }
        if (removed.get() > 0) {
            log.info("Removed {} unreferenced eKYC images", removed.get());
        }
        return removed.get();
    }

    private boolean canDelete(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String[] segments = key.split("/");
        if (segments.length != 3) {
            return false;
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(segments[1]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String processing = EkycVerificationStatus.PROCESSING.name();
        // Check in-flight submissions before references. Finalization commits the keys and the new status together.
        boolean inFlight = "worker".equals(segments[0])
                ? workers.existsByWorker_WorkerIdAndStatus(ownerId, processing)
                : organizers.existsByEventOrganizer_EoIdAndStatus(ownerId, processing);
        return !inFlight && !workers.existsImageReference(key) && !organizers.existsImageReference(key);
    }
}
