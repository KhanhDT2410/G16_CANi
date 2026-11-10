package com.cani.service;

import com.cani.client.ekyc.EkycProvider;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.dto.ekyc.EoProfileDto;
import com.cani.entity.EventOrganizer;
import com.cani.entity.EoEkycVerification;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.UserRepository;
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
public class EventOrganizerEkycService {

    private final EventOrganizerRepository eventOrganizerRepository;
    private final EoEkycVerificationRepository eoEkycVerificationRepository;
    private final UserRepository userRepository;
    private final EkycProvider ekycProvider;

    @org.springframework.beans.factory.annotation.Value("${cani.ekyc.strict:false}")
    private boolean ekycStrict;

    /**
     * Nhà tổ chức sự kiện nộp hồ sơ định danh eKYC doanh nghiệp
     */
    @Transactional
    public EoEkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile,
            MultipartFile businessLicenseFile) {

        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin User: " + userId));
                    if (user.getRole() != null && user.getRole() != UserRole.EVENT_ORGANIZER && user.getRole() != UserRole.ADMIN) {
                        throw new BadRequestException("Tài khoản không phải là Nhà tổ chức sự kiện");
                    }
                    return eventOrganizerRepository.save(EventOrganizer.builder().user(user).build());
                });

        validateFile(frontFile, "Ảnh mặt trước CCCD người đại diện");
        validateFile(backFile, "Ảnh mặt sau CCCD người đại diện");
        validateFile(selfieFile, "Ảnh selfie khuôn mặt người đại diện");

        try {
            byte[] frontBytes = frontFile.getBytes();
            byte[] backBytes = backFile.getBytes();
            byte[] selfieBytes = selfieFile.getBytes();

            // 1. Phân tích OCR và so khớp khuôn mặt qua EkycProvider (VNPT / Mock)
            EkycIdentityResultDto result = ekycProvider.verifyIdentity(
                    frontBytes, frontFile.getContentType(),
                    backBytes, backFile.getContentType(),
                    selfieBytes, selfieFile.getContentType());

            // Đồng bộ thông tin người đại diện nếu là chế độ Mock để phản ánh đúng người dùng đăng nhập
            if ("MOCK_EKYC".equalsIgnoreCase(ekycProvider.getProviderName())) {
                if (eo.getUser() != null && eo.getUser().getFullName() != null && !eo.getUser().getFullName().isBlank()) {
                    result.setFullName(eo.getUser().getFullName().toUpperCase());
                }
                int seed = eo.getEoId() != null ? eo.getEoId().hashCode() : userId.hashCode();
                String uniqueSuffix = String.format("%06d", Math.abs(seed) % 1_000_000);
                result.setIdNumber("001202" + uniqueSuffix);
                if (ekycStrict) {
                    result.setRoutingDecision("MANUAL_REVIEW");
                    result.setRoutingReason("Chế độ eKYC nghiêm ngặt (EKYC_STRICT=true): Hồ sơ chờ CSKH/Admin thẩm định thủ công.");
                } else {
                    result.setRoutingDecision("AUTO_APPROVED");
                    result.setRoutingReason("Hồ sơ đã được đối soát hợp lệ qua hệ thống eKYC.");
                }
            }

            // 2. Băm bảo mật và che số CCCD (Nghị định 13/2023/NĐ-CP)
            String rawIdNumber = result.getIdNumber();
            String idHash = hashCccd(rawIdNumber);
            String maskedNumber = maskCccd(rawIdNumber);

            // 3. Kiểm tra chống gian lận
            if (idHash != null && eoEkycVerificationRepository.existsByCccdNumberHashAndStatus(idHash, "APPROVED")) {
                result.setRoutingDecision("REJECTED");
                result.setRoutingReason("Số CCCD người đại diện này đã được xác thực cho một tổ chức khác trong hệ thống.");
            }

            Map<String, Object> fraudFlags = new HashMap<>();
            fraudFlags.put("isCutCorner", result.isCutCorner());
            fraudFlags.put("isScreenCaptured", result.isScreenCaptured());
            fraudFlags.put("isPhotoshoped", result.isPhotoshoped());
            fraudFlags.put("isExpired", result.isExpired());

            String decision = result.getRoutingDecision();
            String status;
            if ("AUTO_APPROVED".equalsIgnoreCase(decision)) {
                status = "APPROVED";
            } else if ("REJECTED".equalsIgnoreCase(decision)) {
                status = "REJECTED";
            } else {
                status = "PENDING_REVIEW";
            }

            // Nếu nộp giấy phép ĐKKD (Hộ kinh doanh / Doanh nghiệp), bắt buộc chuyển PENDING_REVIEW để Admin thẩm định và xác nhận
            boolean hasBusinessLicense = (businessLicenseFile != null && !businessLicenseFile.isEmpty());
            if (hasBusinessLicense && !"REJECTED".equals(status)) {
                decision = "MANUAL_REVIEW";
                status = "PENDING_REVIEW";
                result.setRoutingDecision("MANUAL_REVIEW");
                result.setRoutingReason("Hồ sơ đăng ký Hộ kinh doanh kèm Giấy phép ĐKKD đang chờ Admin kiểm tra và xác nhận.");
            }

            // Mock link storage (Trong thực tế sẽ upload lên S3/Firebase/Supabase Storage)
            String frontUrl = "https://storage.cani.vn/ekyc/eo_front_" + UUID.randomUUID() + ".jpg";
            String backUrl = "https://storage.cani.vn/ekyc/eo_back_" + UUID.randomUUID() + ".jpg";
            String selfieUrl = "https://storage.cani.vn/ekyc/eo_selfie_" + UUID.randomUUID() + ".jpg";
            String businessLicenseUrl = (businessLicenseFile != null && !businessLicenseFile.isEmpty())
                    ? "https://storage.cani.vn/ekyc/eo_license_" + UUID.randomUUID() + ".jpg"
                    : null;

            EoEkycVerification record = EoEkycVerification.builder()
                    .eventOrganizer(eo)
                    .representativeName(result.getFullName() != null ? result.getFullName() : eo.getUser().getFullName())
                    .cccdNumberMasked(maskedNumber)
                    .cccdNumberHash(idHash)
                    .dob(result.getDob())
                    .gender(result.getGender())
                    .nationality(result.getNationality() != null ? result.getNationality() : "Việt Nam")
                    .originAddress(result.getOriginAddress())
                    .residenceAddress(result.getResidenceAddress())
                    .expiryDate(result.getExpiryDate())
                    .issueDate(result.getIssueDate())
                    .frontImageUrl(frontUrl)
                    .backImageUrl(backUrl)
                    .selfieImageUrl(selfieUrl)
                    .businessLicenseUrl(businessLicenseUrl)
                    .faceMatchScore(BigDecimal.valueOf(result.getFaceMatchScore()))
                    .ocrConfidence(BigDecimal.valueOf(result.getOcrConfidence()))
                    .fraudFlags(fraudFlags)
                    .routingDecision(decision)
                    .routingReason(result.getRoutingReason())
                    .status(status)
                    .build();

            record = eoEkycVerificationRepository.save(record);

            if ("APPROVED".equals(status)) {
                eo.setVerificationStatus(VerificationStatus.APPROVED);
                eo.setVerifiedAt(Instant.now());
                eventOrganizerRepository.save(eo);
            }

            log.info("EO {} đã nộp eKYC: Status={}, Decision={}", eo.getEoId(), status, decision);
            return mapToDto(record);

        } catch (Exception e) {
            log.error("Lỗi xử lý file upload khi nộp EO eKYC: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể đọc và xử lý ảnh CCCD/GPKD: " + e.getMessage());
        }
    }

    /**
     * Lấy trạng thái hồ sơ eKYC hiện tại của Nhà tổ chức sự kiện
     */
    @Transactional
    public EoEkycVerificationResultDto getMyEkycStatus(UUID userId) {
        EventOrganizer eo = getOrCreateEo(userId);

        return eoEkycVerificationRepository.findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId())
                .map(this::mapToDto)
                .orElse(EoEkycVerificationResultDto.builder()
                        .eoId(eo.getEoId())
                        .companyName(eo.getCompanyName())
                        .status("NOT_SUBMITTED")
                        .routingReason("Doanh nghiệp chưa nộp hồ sơ xác thực định danh eKYC")
                        .build());
    }

    /**
     * Lấy thông tin hồ sơ doanh nghiệp (Profile) của EO
     */
    @Transactional
    public EoProfileDto getEoProfile(UUID userId) {
        EventOrganizer eo = getOrCreateEo(userId);
        User u = eo.getUser();

        EoEkycVerification latestVerification = eoEkycVerificationRepository
                .findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId())
                .orElse(null);

        String businessLicenseUrl = latestVerification != null ? latestVerification.getBusinessLicenseUrl() : null;
        String businessVerificationStatus = "NOT_REGISTERED";
        if (eo.getTaxId() != null && !eo.getTaxId().isBlank()) {
            if (latestVerification != null && latestVerification.getBusinessLicenseUrl() != null) {
                businessVerificationStatus = latestVerification.getStatus(); // "PENDING_REVIEW", "APPROVED", "REJECTED"
            } else {
                businessVerificationStatus = "AWAITING_LICENSE_UPLOAD";
            }
        }

        return EoProfileDto.builder()
                .eoId(eo.getEoId())
                .userId(u != null ? u.getUserId() : userId)
                .companyName(eo.getCompanyName())
                .taxId(eo.getTaxId())
                .address(eo.getAddress())
                .fullName(u != null ? u.getFullName() : null)
                .phone(u != null ? u.getPhone() : null)
                .email(u != null ? u.getEmail() : null)
                .verificationStatus(eo.getVerificationStatus() != null ? eo.getVerificationStatus().name() : "PENDING")
                .verifiedAt(eo.getVerifiedAt())
                .ratingAvg(eo.getRatingAvg())
                .ratingCount(eo.getRatingCount())
                .totalEventsCompleted(eo.getTotalEventsCompleted())
                .businessLicenseUrl(businessLicenseUrl)
                .businessVerificationStatus(businessVerificationStatus)
                .build();
    }

    /**
     * Cập nhật thông tin hồ sơ doanh nghiệp của EO
     */
    @Transactional
    public EoProfileDto updateEoProfile(UUID userId, EoProfileDto dto) {
        EventOrganizer eo = getOrCreateEo(userId);
        if (dto.getCompanyName() != null) eo.setCompanyName(dto.getCompanyName().trim());
        if (dto.getTaxId() != null) eo.setTaxId(dto.getTaxId().trim());
        if (dto.getAddress() != null) eo.setAddress(dto.getAddress().trim());
        eventOrganizerRepository.save(eo);

        User u = eo.getUser();
        if (u != null) {
            boolean userChanged = false;
            if (dto.getFullName() != null && !dto.getFullName().isBlank()) {
                u.setFullName(dto.getFullName().trim());
                userChanged = true;
            }
            if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
                u.setPhone(dto.getPhone().trim());
                userChanged = true;
            }
            if (userChanged) {
                userRepository.save(u);
            }
        }

        log.info("EO {} đã cập nhật thông tin doanh nghiệp (Profile)", eo.getEoId());
        return getEoProfile(userId);
    }

    private EventOrganizer getOrCreateEo(UUID userId) {
        return eventOrganizerRepository.findByUser_UserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin User: " + userId));
                    if (user.getRole() != null && user.getRole() != UserRole.EVENT_ORGANIZER && user.getRole() != UserRole.ADMIN) {
                        throw new BadRequestException("Tài khoản không phải là Nhà tổ chức sự kiện");
                    }
                    return eventOrganizerRepository.save(EventOrganizer.builder().user(user).build());
                });
    }

    /**
     * Lấy danh sách hàng đợi hồ sơ eKYC đang chờ duyệt (PENDING_REVIEW)
     */
    @Transactional(readOnly = true)
    public List<EoEkycVerificationResultDto> getPendingEkycQueue() {
        return eoEkycVerificationRepository.findByStatusOrderByCreatedAtAsc("PENDING_REVIEW")
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Admin/Customer Care thẩm định thủ công hồ sơ eKYC của Nhà tổ chức
     */
    @Transactional
    public EoEkycVerificationResultDto adminReviewEkyc(
            UUID adminUserId,
            UUID verificationId,
            EkycAdminReviewRequestDto reviewRequest) {

        EoEkycVerification record = eoEkycVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC có ID: " + verificationId));

        if (!"PENDING_REVIEW".equals(record.getStatus())) {
            throw new BadRequestException("Hồ sơ này đã được xử lý trước đó với trạng thái: " + record.getStatus());
        }

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Admin: " + adminUserId));

        String decision = reviewRequest.getDecision().toUpperCase().trim();
        EventOrganizer eo = record.getEventOrganizer();

        if ("APPROVED".equals(decision)) {
            record.setStatus("APPROVED");
            record.setRoutingDecision("MANUAL_APPROVED");
            eo.setVerificationStatus(VerificationStatus.APPROVED);
            eo.setVerifiedAt(Instant.now());
        } else if ("REJECTED".equals(decision)) {
            record.setStatus("REJECTED");
            record.setRoutingDecision("MANUAL_REJECTED");
            eo.setVerificationStatus(VerificationStatus.REJECTED);
        } else {
            throw new BadRequestException("Quyết định duyệt chỉ chấp nhận APPROVED hoặc REJECTED");
        }

        record.setReviewedBy(adminUser);
        record.setReviewNotes(reviewRequest.getReviewNotes());

        eoEkycVerificationRepository.save(record);
        eventOrganizerRepository.save(eo);

        log.info("Admin {} đã duyệt hồ sơ EO eKYC {} với trạng thái {}", adminUserId, verificationId, record.getStatus());
        return mapToDto(record);
    }

    private EoEkycVerificationResultDto mapToDto(EoEkycVerification record) {
        return EoEkycVerificationResultDto.builder()
                .verificationId(record.getId())
                .eoId(record.getEventOrganizer().getEoId())
                .companyName(record.getEventOrganizer().getCompanyName())
                .representativeName(record.getRepresentativeName())
                .cccdNumberMasked(record.getCccdNumberMasked())
                .dob(record.getDob())
                .gender(record.getGender())
                .nationality(record.getNationality())
                .originAddress(record.getOriginAddress())
                .residenceAddress(record.getResidenceAddress())
                .expiryDate(record.getExpiryDate())
                .issueDate(record.getIssueDate())
                .frontImageUrl(record.getFrontImageUrl())
                .backImageUrl(record.getBackImageUrl())
                .selfieImageUrl(record.getSelfieImageUrl())
                .businessLicenseUrl(record.getBusinessLicenseUrl())
                .faceMatchScore(record.getFaceMatchScore())
                .ocrConfidence(record.getOcrConfidence())
                .fraudFlags(record.getFraudFlags())
                .routingDecision(record.getRoutingDecision())
                .routingReason(record.getRoutingReason())
                .status(record.getStatus())
                .reviewedBy(record.getReviewedBy() != null ? record.getReviewedBy().getUserId() : null)
                .reviewNotes(record.getReviewNotes())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(fieldName + " không được để trống");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new BadRequestException(fieldName + " phải là định dạng ảnh hợp lệ (JPEG, PNG, WEBP)");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException(fieldName + " vượt quá dung lượng tối đa 5MB");
        }
    }

    private String hashCccd(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(idNumber.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String maskCccd(String idNumber) {
        if (idNumber == null || idNumber.length() < 6) return idNumber;
        int len = idNumber.length();
        return idNumber.substring(0, 3) + "******" + idNumber.substring(len - 3);
    }
}
