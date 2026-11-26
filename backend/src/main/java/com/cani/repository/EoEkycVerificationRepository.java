package com.cani.repository;

import com.cani.entity.EoEkycVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EoEkycVerificationRepository extends JpaRepository<EoEkycVerification, UUID> {

    /**
     * Lấy hồ sơ eKYC mới nhất của Nhà tổ chức sự kiện
     */
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"eventOrganizer", "eventOrganizer.user"})
    Optional<EoEkycVerification> findById(UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"eventOrganizer", "eventOrganizer.user"})
    @org.springframework.data.jpa.repository.Query("select v from EoEkycVerification v where v.id = :id")
    Optional<EoEkycVerification> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<EoEkycVerification> findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(UUID eoId);

    /**
     * Kiểm tra xem số CCCD này đã được duyệt eKYC thành công cho tổ chức nào khác chưa (chống gian lận)
     */
    boolean existsByCccdNumberHashInAndStatus(Collection<String> cccdNumberHashes, String status);

    boolean existsByCccdNumberHashInAndStatusInAndEventOrganizer_EoIdNot(
            Collection<String> cccdNumberHashes, Collection<String> statuses, UUID eoId);

    boolean existsByCccdNumberHashInAndStatusInAndEventOrganizer_User_UserIdNot(
            Collection<String> cccdNumberHashes, Collection<String> statuses, UUID userId);

    List<EoEkycVerification> findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(Instant cutoff);

    long countByImagesPurgedAtIsNullAndImageRetentionUntilIsNull();

    @org.springframework.data.jpa.repository.Query("""
            select v.status, count(v) from EoEkycVerification v
            where v.imagesPurgedAt is null and v.imageRetentionUntil is null
            group by v.status
            """)
    List<Object[]> countLegacyNullRetentionByStatus();

    @org.springframework.data.jpa.repository.Query("""
            select v.id from EoEkycVerification v
            where v.status = 'PROCESSING'
              and coalesce(v.processingStartedAt, v.updatedAt, v.createdAt) < :cutoff
            """)
    List<UUID> findExpiredProcessingIds(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);

    /**
     * Đếm số lượng hồ sơ eKYC theo trạng thái
     */
    long countByStatus(String status);
    boolean existsByEventOrganizer_EoIdAndStatus(UUID ownerId, String status);

    @org.springframework.data.jpa.repository.Query("""
            select (count(v) > 0) from EoEkycVerification v
            where v.frontImageUrl = :key or v.backImageUrl = :key or v.selfieImageUrl = :key
            """)
    boolean existsImageReference(String key);
}
