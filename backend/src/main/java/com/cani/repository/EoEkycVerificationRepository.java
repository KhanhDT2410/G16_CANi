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

    Optional<EoEkycVerification> findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(UUID eoId);

    Optional<EoEkycVerification> findTopByEventOrganizer_EoIdAndStatusInOrderByCreatedAtDesc(
            UUID eoId, Collection<String> statuses);

    /**
     * Kiểm tra xem số CCCD này đã được duyệt eKYC thành công cho tổ chức nào khác chưa (chống gian lận)
     */
    boolean existsByCccdNumberHashAndStatus(String cccdNumberHash, String status);

    boolean existsByCccdNumberHashInAndStatus(Collection<String> cccdNumberHashes, String status);

    boolean existsByCccdNumberHashAndStatusInAndEventOrganizer_EoIdNot(
            String cccdNumberHash, Collection<String> statuses, UUID eoId);

    boolean existsByCccdNumberHashInAndStatusInAndEventOrganizer_EoIdNot(
            Collection<String> cccdNumberHashes, Collection<String> statuses, UUID eoId);

    boolean existsByCccdNumberHashAndStatusInAndEventOrganizer_User_UserIdNot(
            String cccdNumberHash, Collection<String> statuses, UUID userId);

    boolean existsByCccdNumberHashInAndStatusInAndEventOrganizer_User_UserIdNot(
            Collection<String> cccdNumberHashes, Collection<String> statuses, UUID userId);

    /**
     * Lấy danh sách hồ sơ eKYC chờ duyệt (PENDING_REVIEW) cho Admin thẩm định
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "eventOrganizer")
    List<EoEkycVerification> findByStatusOrderByCreatedAtAsc(String status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<EoEkycVerification> findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(Instant cutoff);

    /**
     * Đếm số lượng hồ sơ eKYC theo trạng thái
     */
    long countByStatus(String status);
}
