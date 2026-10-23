package com.cani.repository;

import com.cani.entity.EoEkycVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EoEkycVerificationRepository extends JpaRepository<EoEkycVerification, UUID> {

    /**
     * Lấy hồ sơ eKYC mới nhất của Nhà tổ chức sự kiện
     */
    Optional<EoEkycVerification> findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(UUID eoId);

    /**
     * Kiểm tra xem số CCCD này đã được duyệt eKYC thành công cho tổ chức nào khác chưa (chống gian lận)
     */
    boolean existsByCccdNumberHashAndStatus(String cccdNumberHash, String status);

    /**
     * Lấy danh sách hồ sơ eKYC chờ duyệt (PENDING_REVIEW) cho Admin thẩm định
     */
    List<EoEkycVerification> findByStatusOrderByCreatedAtAsc(String status);
}
