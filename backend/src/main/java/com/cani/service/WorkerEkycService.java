package com.cani.service;

import com.cani.client.ekyc.EkycProvider;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.KycStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerEkycService {

    private final WorkerRepository workerRepository;
    private final WorkerEkycVerificationRepository ekycVerificationRepository;
    private final EkycProvider ekycProvider;
    private final UserRepository userRepository;

    /**
     * Worker nộp hồ sơ eKYC: Mặt trước CCCD, Mặt sau CCCD và Ảnh Selfie chân dung
     */
    @Transactional
    public EkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile) {
        return submitEkyc(userId, frontFile, backFile, selfieFile, null, null, null, null, null, null, null, null);
    }

    @Transactional
    public EkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile,
            String confirmedCccd,
            String confirmedFullName,
            String confirmedDob,
            String confirmedGender,
            String confirmedOriginAddress,
            String confirmedResidenceAddress,
            String confirmedIssueDate,
            String confirmedExpiryDate) {

        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy User cho User ID: " + userId));
                    return workerRepository.save(Worker.builder().user(user).isOnline(false).build());
                });

        validateFile(frontFile, "Ảnh mặt trước CCCD");
        validateFile(backFile, "Ảnh mặt sau CCCD");
        validateFile(selfieFile, "Ảnh chân dung Selfie");

        try {
            byte[] frontBytes = frontFile.getBytes();
            byte[] backBytes = backFile.getBytes();
            byte[] selfieBytes = selfieFile.getBytes();

            // 1. Gửi sang eKYC Provider (VNPT eKYC / Mock) phân tích OCR, Fraud Detection và Face Matching
            EkycIdentityResultDto ekycResult = ekycProvider.verifyIdentity(
                    frontBytes, frontFile.getContentType(),
                    backBytes, backFile.getContentType(),
                    selfieBytes, selfieFile.getContentType());

            // Đồng bộ tên worker nếu là chế độ Mock để phản ánh đúng người dùng đăng nhập
            if ("MOCK_EKYC".equalsIgnoreCase(ekycProvider.getProviderName())) {
                if (worker.getFullName() != null && !worker.getFullName().isBlank()) {
                    ekycResult.setFullName(worker.getFullName().toUpperCase());
                }
                String uniqueSuffix = String.format("%06d", Math.abs(worker.getWorkerId().hashCode()) % 1_000_000);
                ekycResult.setIdNumber("001202" + uniqueSuffix);
                ekycResult.setRoutingDecision("AUTO_APPROVED");
                ekycResult.setRoutingReason("Hồ sơ đã được đối soát hợp lệ qua hệ thống eKYC.");
            }

            // Ghi đè bằng thông tin người dùng đã xác nhận hoặc quét từ mã QR trên thẻ CCCD gắn chip (nếu có)
            if (confirmedCccd != null && !confirmedCccd.isBlank()) {
                ekycResult.setIdNumber(confirmedCccd.trim());
            }
            if (confirmedFullName != null && !confirmedFullName.isBlank()) {
                String cleanName = confirmedFullName.trim().toUpperCase();
                ekycResult.setFullName(cleanName);
                if (worker.getUser() != null) {
                    worker.getUser().setFullName(cleanName);
                }
            }
            if (confirmedDob != null && !confirmedDob.isBlank()) {
                ekycResult.setDob(confirmedDob.trim());
            }
            if (confirmedGender != null && !confirmedGender.isBlank()) {
                ekycResult.setGender(confirmedGender.trim());
            }
            if (confirmedOriginAddress != null && !confirmedOriginAddress.isBlank()) {
                ekycResult.setOriginAddress(confirmedOriginAddress.trim());
            }
            if (confirmedResidenceAddress != null && !confirmedResidenceAddress.isBlank()) {
                ekycResult.setResidenceAddress(confirmedResidenceAddress.trim());
            }
            if (confirmedIssueDate != null && !confirmedIssueDate.isBlank()) {
                ekycResult.setIssueDate(confirmedIssueDate.trim());
            }
            if (confirmedExpiryDate != null && !confirmedExpiryDate.isBlank()) {
                ekycResult.setExpiryDate(confirmedExpiryDate.trim());
            }

            // 2. Băm bảo mật và che số CCCD (Nghị định 13/2023/NĐ-CP)
            String rawIdNumber = ekycResult.getIdNumber();
            String idHash = hashCccd(rawIdNumber);
            String maskedNumber = maskCccd(rawIdNumber);

            // 3. Kiểm tra chống gian lận dùng trùng CCCD cho nhiều tài khoản (trừ khi là chính worker này nộp lại)
            if (idHash != null && ekycVerificationRepository.existsByCccdNumberHashAndStatusAndWorker_WorkerIdNot(idHash, "APPROVED", worker.getWorkerId())) {
                ekycResult.setRoutingDecision("REJECTED");
                ekycResult.setRoutingReason("Số Căn cước công dân này đã được liên kết với một tài khoản Worker khác trong hệ thống.");
            }

            Map<String, Object> fraudFlags = new HashMap<>();
            fraudFlags.put("isCutCorner", ekycResult.isCutCorner());
            fraudFlags.put("isScreenCaptured", ekycResult.isScreenCaptured());
            fraudFlags.put("isPhotoshoped", ekycResult.isPhotoshoped());
            fraudFlags.put("isExpired", ekycResult.isExpired());

            String decision = ekycResult.getRoutingDecision();
            String status;
            if ("AUTO_APPROVED".equalsIgnoreCase(decision)) {
                status = "APPROVED";
            } else if ("REJECTED".equalsIgnoreCase(decision)) {
                status = "REJECTED";
            } else {
                status = "PENDING_REVIEW";
            }

            // Mock link storage (Trong thực tế sẽ upload lên S3/Firebase/Supabase Storage)
            String frontUrl = "https://storage.cani.vn/ekyc/front_" + UUID.randomUUID() + ".jpg";
            String backUrl = "https://storage.cani.vn/ekyc/back_" + UUID.randomUUID() + ".jpg";
            String selfieUrl = "https://storage.cani.vn/ekyc/selfie_" + UUID.randomUUID() + ".jpg";

            // 4. Lưu hồ sơ eKYC
            WorkerEkycVerification verification = WorkerEkycVerification.builder()
                    .worker(worker)
                    .cccdNumberMasked(maskedNumber)
                    .cccdNumberHash(idHash)
                    .fullName(ekycResult.getFullName() != null ? ekycResult.getFullName() : worker.getFullName())
                    .dob(ekycResult.getDob())
                    .gender(ekycResult.getGender())
                    .nationality(ekycResult.getNationality())
                    .originAddress(ekycResult.getOriginAddress())
                    .residenceAddress(ekycResult.getResidenceAddress())
                    .expiryDate(ekycResult.getExpiryDate())
                    .issueDate(ekycResult.getIssueDate())
                    .identificationFeatures(ekycResult.getIdentificationFeatures())
                    .frontImageUrl(frontUrl)
                    .backImageUrl(backUrl)
                    .selfieImageUrl(selfieUrl)
                    .faceMatchScore(BigDecimal.valueOf(ekycResult.getFaceMatchScore()))
                    .ocrConfidence(BigDecimal.valueOf(ekycResult.getOcrConfidence()))
                    .fraudFlags(fraudFlags)
                    .routingDecision(decision)
                    .routingReason(ekycResult.getRoutingReason())
                    .status(status)
                    .build();

            verification = ekycVerificationRepository.save(verification);

            // 5. Đồng bộ trạng thái vào bảng Worker
            if ("APPROVED".equals(status)) {
                worker.setKycStatus(KycStatus.APPROVED);
                worker.setCccdNumber(maskedNumber);
            } else if ("PENDING_REVIEW".equals(status)) {
                worker.setKycStatus(KycStatus.PENDING);
            } else {
                worker.setKycStatus(KycStatus.REJECTED);
            }
            workerRepository.save(worker);

            log.info("Worker {} đã nộp eKYC: Quyết định eKYC={}, Trạng thái={}", worker.getWorkerId(), decision, status);

            return mapToDto(verification);

        } catch (Exception e) {
            log.error("Lỗi khi xử lý hồ sơ eKYC của Worker {}: {}", worker.getWorkerId(), e.getMessage(), e);
            throw new BadRequestException("Không thể xử lý ảnh CCCD/Selfie: " + e.getMessage());
        }
    }

    /**
     * Lấy kết quả thẩm định eKYC gần nhất của Worker
     */
    @Transactional(readOnly = true)
    public EkycVerificationResultDto getMyEkycStatus(UUID userId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Worker"));

        return ekycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Admin / Customer Care duyệt thủ công hồ sơ eKYC (Human-in-the-loop)
     */
    @Transactional
    public EkycVerificationResultDto adminReviewEkyc(UUID adminUserId, UUID verificationId, EkycAdminReviewRequestDto dto) {
        WorkerEkycVerification verification = ekycVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC: " + verificationId));

        String decision = dto.getDecision().toUpperCase();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new BadRequestException("Quyết định duyệt chỉ chấp nhận APPROVED hoặc REJECTED");
        }

        verification.setStatus(decision);
        verification.setReviewedBy(adminUserId);
        verification.setReviewNotes(dto.getReviewNotes());
        verification = ekycVerificationRepository.save(verification);

        // Cập nhật trạng thái Worker
        Worker worker = verification.getWorker();
        if ("APPROVED".equals(decision)) {
            worker.setKycStatus(KycStatus.APPROVED);
            worker.setCccdNumber(verification.getCccdNumberMasked());
        } else {
            worker.setKycStatus(KycStatus.REJECTED);
        }
        workerRepository.save(worker);


        log.info("Admin {} đã duyệt hồ sơ eKYC {}: {}", adminUserId, verificationId, decision);
        return mapToDto(verification);
    }

    /**
     * Danh sách hàng đợi hồ sơ eKYC chờ Admin duyệt
     */
    @Transactional(readOnly = true)
    public List<EkycVerificationResultDto> getPendingEkycQueue() {
        return ekycVerificationRepository.findByStatusOrderByCreatedAtDesc("PENDING_REVIEW").stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(fieldName + " không được để trống");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException(fieldName + " không được vượt quá 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.contains("pdf"))) {
            throw new BadRequestException(fieldName + " phải là tệp hình ảnh hợp lệ (JPG, PNG)");
        }
    }

    private String hashCccd(String cccd) {
        if (cccd == null || cccd.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cccd.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String maskCccd(String cccd) {
        if (cccd == null || cccd.length() < 6) return cccd;
        String clean = cccd.trim();
        return clean.substring(0, 6) + "******" + (clean.length() > 9 ? clean.substring(clean.length() - 3) : "");
    }

    private EkycVerificationResultDto mapToDto(WorkerEkycVerification v) {
        return EkycVerificationResultDto.builder()
                .verificationId(v.getId())
                .workerId(v.getWorker() != null ? v.getWorker().getWorkerId() : null)
                .workerName(v.getWorker() != null ? v.getWorker().getFullName() : "")
                .cccdNumberMasked(v.getCccdNumberMasked())
                .fullName(v.getFullName())
                .dob(v.getDob())
                .gender(v.getGender())
                .nationality(v.getNationality())
                .originAddress(v.getOriginAddress())
                .residenceAddress(v.getResidenceAddress())
                .expiryDate(v.getExpiryDate())
                .issueDate(v.getIssueDate())
                .identificationFeatures(v.getIdentificationFeatures())
                .frontImageUrl(v.getFrontImageUrl())
                .backImageUrl(v.getBackImageUrl())
                .selfieImageUrl(v.getSelfieImageUrl())
                .faceMatchScore(v.getFaceMatchScore())
                .ocrConfidence(v.getOcrConfidence())
                .fraudFlags(v.getFraudFlags())
                .routingDecision(v.getRoutingDecision())
                .routingReason(v.getRoutingReason())
                .status(v.getStatus())
                .reviewedBy(v.getReviewedBy())
                .reviewNotes(v.getReviewNotes())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
