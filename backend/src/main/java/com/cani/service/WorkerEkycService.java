package com.cani.service;

import com.cani.client.gemini.GeminiVisionClient;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.dto.ekyc.GeminiEkycResultDto;
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
    private final GeminiVisionClient geminiVisionClient;
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

            // 1. Gửi sang Gemini Vision AI phân tích OCR, Fraud Detection và Face Matching
            GeminiEkycResultDto aiResult = geminiVisionClient.verifyEkycIdentity(
                    frontBytes, frontFile.getContentType(),
                    backBytes, backFile.getContentType(),
                    selfieBytes, selfieFile.getContentType());

            // 2. Băm bảo mật và che số CCCD (Nghị định 13/2023/NĐ-CP)
            String rawIdNumber = aiResult.getIdNumber();
            String idHash = hashCccd(rawIdNumber);
            String maskedNumber = maskCccd(rawIdNumber);

            // 3. Kiểm tra chống gian lận dùng trùng CCCD cho nhiều tài khoản
            if (idHash != null && ekycVerificationRepository.existsByCccdNumberHashAndStatus(idHash, "APPROVED")) {
                aiResult.setRoutingDecision("REJECTED");
                aiResult.setRoutingReason("Số Căn cước công dân này đã được liên kết với một tài khoản Worker khác trong hệ thống.");
            }

            Map<String, Object> fraudFlags = new HashMap<>();
            fraudFlags.put("isCutCorner", aiResult.isCutCorner());
            fraudFlags.put("isScreenCaptured", aiResult.isScreenCaptured());
            fraudFlags.put("isPhotoshoped", aiResult.isPhotoshoped());
            fraudFlags.put("isExpired", aiResult.isExpired());

            String decision = aiResult.getRoutingDecision();
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
                    .fullName(aiResult.getFullName() != null ? aiResult.getFullName() : worker.getFullName())
                    .dob(aiResult.getDob())
                    .gender(aiResult.getGender())
                    .nationality(aiResult.getNationality())
                    .originAddress(aiResult.getOriginAddress())
                    .residenceAddress(aiResult.getResidenceAddress())
                    .expiryDate(aiResult.getExpiryDate())
                    .issueDate(aiResult.getIssueDate())
                    .identificationFeatures(aiResult.getIdentificationFeatures())
                    .frontImageUrl(frontUrl)
                    .backImageUrl(backUrl)
                    .selfieImageUrl(selfieUrl)
                    .faceMatchScore(BigDecimal.valueOf(aiResult.getFaceMatchScore()))
                    .ocrConfidence(BigDecimal.valueOf(aiResult.getOcrConfidence()))
                    .fraudFlags(fraudFlags)
                    .routingDecision(decision)
                    .routingReason(aiResult.getRoutingReason())
                    .status(status)
                    .build();

            verification = ekycVerificationRepository.save(verification);

            // 5. Đồng bộ trạng thái vào bảng Worker
            if ("APPROVED".equals(status)) {
                worker.setKycStatus(KycStatus.APPROVED);
                worker.setCccdNumber(maskedNumber);
                worker.setCccdFrontUrl(frontUrl);
                worker.setCccdBackUrl(backUrl);
                worker.setSelfieUrl(selfieUrl);
                worker.setKycSubmittedAt(Instant.now());
            } else if ("PENDING_REVIEW".equals(status)) {
                worker.setKycStatus(KycStatus.PENDING);
                worker.setKycSubmittedAt(Instant.now());
            } else {
                worker.setKycStatus(KycStatus.REJECTED);
            }
            workerRepository.save(worker);

            log.info("Worker {} đã nộp eKYC: Quyết định AI={}, Trạng thái={}", worker.getWorkerId(), decision, status);

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
            worker.setCccdFrontUrl(verification.getFrontImageUrl());
            worker.setCccdBackUrl(verification.getBackImageUrl());
            worker.setSelfieUrl(verification.getSelfieImageUrl());
            worker.setKycReviewedBy(adminUserId);
        } else {
            worker.setKycStatus(KycStatus.REJECTED);
            worker.setKycReviewedBy(adminUserId);
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
