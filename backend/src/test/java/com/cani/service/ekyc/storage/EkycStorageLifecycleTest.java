package com.cani.service.ekyc.storage;

import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.EkycVerificationStatus;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EkycStorageLifecycleTest {
    @Test
    void expiredSignatureWithOriginalTimestampIsRejected() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        var issuer = new EkycImageAccessTokenService("synthetic-secret", 10, Clock.fixed(start, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();
        URI url = URI.create(issuer.generateSignedImageUrl("worker", id, "front", "ref", "PENDING_REVIEW"));
        String[] query = url.getRawQuery().split("&");
        assertThat(issuer.isValidRequestUri(url.getPath(), query[0].substring(8), query[1].substring(4))).isTrue();
        var afterExpiry = new EkycImageAccessTokenService("synthetic-secret", 10,
                Clock.fixed(start.plusSeconds(10), ZoneOffset.UTC));
        assertThat(afterExpiry.isValidRequestUri(url.getPath(), query[0].substring(8), query[1].substring(4))).isFalse();
    }

    @Test
    void removesAgedOrphansAndPreservesReferencesAndInFlightFiles(@TempDir Path directory) throws Exception {
        var storage = new LocalPrivateEkycImageStorage(directory.toString());
        var workers = mock(WorkerEkycVerificationRepository.class);
        var organizers = mock(EoEkycVerificationRepository.class);
        var cleanup = new EkycOrphanImageCleanup(storage, workers, organizers, 1);
        UUID activeOwner = UUID.randomUUID();
        var active = storage.store("worker", activeOwner, "front", new byte[] {1}, "jpg", "image/jpeg");
        var referenced = storage.store("worker", UUID.randomUUID(), "back", new byte[] {2}, "jpg", "image/jpeg");
        var orphan = storage.store("eo", UUID.randomUUID(), "selfie", new byte[] {3}, "jpg", "image/jpeg");
        var recent = storage.store("eo", UUID.randomUUID(), "front", new byte[] {4}, "jpg", "image/jpeg");
        for (var image : List.of(active, referenced, orphan)) {
            Files.setLastModifiedTime(directory.resolve(image.getObjectKey()),
                    FileTime.from(Instant.now().minusSeconds(7200)));
        }
        when(workers.existsByWorker_WorkerIdAndStatus(activeOwner, "PROCESSING")).thenReturn(true);
        when(workers.existsImageReference(referenced.getObjectKey())).thenReturn(true);
        assertThat(cleanup.purgeOrphans()).isEqualTo(1);
        assertThat(storage.exists(orphan.getObjectKey())).isFalse();
        assertThat(storage.exists(active.getObjectKey())).isTrue();
        assertThat(storage.exists(referenced.getObjectKey())).isTrue();
        assertThat(storage.exists(recent.getObjectKey())).isTrue();
    }

    @Test
    void honorsLegalHoldAndRetriesUnlinkFailures() {
        var storage = mock(EkycImageStorage.class);
        var workers = mock(WorkerEkycVerificationRepository.class);
        var organizers = mock(EoEkycVerificationRepository.class);
        var cutoff = Instant.now().minusSeconds(1);
        var held = WorkerEkycVerification.builder().legalHold(true).imageRetentionUntil(cutoff)
                .frontImageUrl("hold").build();
        var expired = WorkerEkycVerification.builder().imageRetentionUntil(cutoff).frontImageUrl("expired").build();
        when(workers.findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(any())).thenReturn(List.of(held, expired));
        when(storage.deleteIfPresent("expired")).thenReturn(false, true);
        when(storage.deleteIfPresent(null)).thenReturn(true);
        var retention = new EkycImageRetentionService(storage, workers, organizers, 0, 0, 0, 0, true);
        assertThat(retention.purgeExpiredImages()).isZero();
        assertThat(expired.getFrontImageUrl()).isEqualTo("expired");
        assertThat(retention.purgeExpiredImages()).isEqualTo(1);
        assertThat(expired.getImagesPurgedAt()).isNotNull();
        assertThat(held.getFrontImageUrl()).isEqualTo("hold");
        verify(storage, never()).deleteIfPresent("hold");
    }

    @Test
    void zeroRetentionNeverReturnsNullAndInventoryIsCountOnly() {
        var storage = mock(EkycImageStorage.class);
        var workers = mock(WorkerEkycVerificationRepository.class);
        var organizers = mock(EoEkycVerificationRepository.class);
        var retention = new EkycImageRetentionService(storage, workers, organizers, 0, 0, 3, 14, true);

        Instant now = Instant.now();
        Instant approvedDeadline = retention.calculateRetentionDeadline(EkycVerificationStatus.APPROVED);
        Instant rejectedDeadline = retention.calculateRetentionDeadline(EkycVerificationStatus.REJECTED);
        Instant pendingReviewDeadline = retention.calculateRetentionDeadline(EkycVerificationStatus.PENDING_REVIEW);
        Instant techFailureDeadline = retention.calculateRetentionDeadline(EkycVerificationStatus.TECHNICAL_FAILURE);

        assertThat(approvedDeadline).isNotNull().isBeforeOrEqualTo(Instant.now().plusSeconds(2));
        assertThat(rejectedDeadline).isNotNull().isBeforeOrEqualTo(Instant.now().plusSeconds(2));
        assertThat(pendingReviewDeadline).isAfter(now.plusSeconds(13 * 86400L));
        assertThat(techFailureDeadline).isAfter(now.plusSeconds(2 * 86400L));

        when(workers.countByImagesPurgedAtIsNullAndImageRetentionUntilIsNull()).thenReturn(4L);
        when(organizers.countByImagesPurgedAtIsNullAndImageRetentionUntilIsNull()).thenReturn(2L);
        when(workers.countLegacyNullRetentionByStatus()).thenReturn(List.<Object[]>of(new Object[]{"APPROVED", 4L}));
        when(organizers.countLegacyNullRetentionByStatus()).thenReturn(List.<Object[]>of(new Object[]{"PENDING_REVIEW", 2L}));

        var inventory = retention.getLegacyNullRetentionInventory();
        assertThat(inventory.getWorkerLegacyNullRetentionCount()).isEqualTo(4L);
        assertThat(inventory.getEoLegacyNullRetentionCount()).isEqualTo(2L);
        assertThat(inventory.getTotalLegacyNullRetentionCount()).isEqualTo(6L);
        verify(storage, never()).deleteIfPresent(any());
        verify(workers, never()).save(any());
        verify(organizers, never()).save(any());
    }
}
