package com.cani.service.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto.ExpiryDateStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.FaceCompareStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.LivenessStatus;
import com.cani.dto.ekyc.EkycUserSubmittedInput;
import com.cani.entity.enums.EkycRoutingDecision;
import com.cani.entity.enums.EkycVerificationStatus;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

/**
 * Bộ máy quyết định trạng thái eKYC thống nhất cho cả Worker và Event Organizer.
 *
 * Quy tắc bắt buộc:
 * - Tách bạch hoàn toàn lỗi kỹ thuật (TECHNICAL_FAILURE) khỏi lỗi nghiệp vụ/gian lận (REJECTED).
 * - Không bao giờ biến mọi trạng thái khác AUTO_APPROVED thành REJECTED.
 * - Chỉ AUTO_APPROVED khi đủ toàn bộ điều kiện: giấy tờ hợp lệ, OCR đủ dữ liệu, ngày hết hạn đọc được
 *   và còn hiệu lực, Face Compare MATCH hợp lệ, Liveness đạt (nếu yêu cầu), không gian lận, không trùng CCCD,
 *   và không có sai lệch nghiêm trọng với dữ liệu QR/nhập tay đối chiếu.
 * - Dữ liệu QR/nhập tay từ client CHỈ dùng để đối chiếu chéo — tuyệt đối không ghi đè dữ liệu OCR của VNPT.
 */
@Component
public class EkycDecisionEngine {

    private final VnptEkycProperties vnptProperties;
    private final EkycIdentityCryptoService cryptoService;

    public EkycDecisionEngine(VnptEkycProperties vnptProperties, EkycIdentityCryptoService cryptoService) {
        this.vnptProperties = vnptProperties;
        this.cryptoService = cryptoService;
    }

    @Value
    @Builder
    public static class DecisionOutcome {
        EkycVerificationStatus status;
        EkycRoutingDecision routingDecision;
        String routingReason;
        String failureCode;
        boolean retryable;
        Map<String, Object> fraudFlags;
    }

    public DecisionOutcome evaluate(
            EkycIdentityResultDto providerResult,
            EkycUserSubmittedInput userInput,
            boolean isDuplicateCccd,
            boolean strictManualReview) {

        Map<String, Object> flags = new LinkedHashMap<>();

        // 1. Lỗi kỹ thuật từ VNPT / kết nối / cấu hình -> TECHNICAL_FAILURE (cho phép thử lại, không phải gian lận)
        if (providerResult == null || providerResult.isTechnicalFailure()) {
            String errorCode = providerResult != null && providerResult.getTechnicalErrorCode() != null
                    ? providerResult.getTechnicalErrorCode()
                    : "VNPT_TECHNICAL_ERROR";
            String reason = providerResult != null && providerResult.getRoutingReason() != null && !providerResult.getRoutingReason().isBlank()
                    ? providerResult.getRoutingReason()
                    : "Hệ thống xác thực VNPT eKYC đang gặp sự cố kỹ thuật tạm thời. Vui lòng thử lại sau.";
            flags.put("technicalFailure", true);
            flags.put("errorCode", errorCode);
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.TECHNICAL_FAILURE)
                    .routingDecision(EkycRoutingDecision.TECHNICAL_FAILURE)
                    .routingReason(reason)
                    .failureCode(errorCode)
                    .retryable(true)
                    .fraudFlags(flags)
                    .build();
        }

        // Ghi nhận cờ kỹ thuật/chống gian lận (không chứa PII)
        flags.put("isCutCorner", providerResult.isCutCorner());
        flags.put("isScreenCaptured", providerResult.isScreenCaptured());
        flags.put("isPhotoshoped", providerResult.isPhotoshoped());
        flags.put("isExpired", providerResult.isExpired());
        flags.put("documentLegal", providerResult.isDocumentLegal());
        flags.put("fakeDocument", providerResult.isFakeDocument());
        flags.put("backCardVerified", providerResult.isBackCardVerified());
        flags.put("backCardTampered", providerResult.isBackCardTampered());
        flags.put("hasBlurryWarning", providerResult.isHasBlurryWarning());
        flags.put("expiryStatus", providerResult.getExpiryStatus() != null ? providerResult.getExpiryStatus().name() : ExpiryDateStatus.UNREADABLE.name());
        flags.put("faceCompareStatus", providerResult.getFaceCompareStatus() != null ? providerResult.getFaceCompareStatus().name() : FaceCompareStatus.MISSING.name());
        flags.put("livenessStatus", providerResult.getLivenessStatus() != null ? providerResult.getLivenessStatus().name() : LivenessStatus.NOT_EVALUATED.name());

        // 2. Đối chiếu chéo với dữ liệu QR/nhập tay (CHỈ đối chiếu, tuyệt đối không ghi đè OCR của VNPT)
        List<String> mismatchFields = crossCheckUserInput(providerResult, userInput);
        if (!mismatchFields.isEmpty()) {
            flags.put("userInputMismatch", true);
            flags.put("mismatchFields", mismatchFields);
        } else {
            flags.put("userInputMismatch", false);
        }

        // 3. Các lỗi kỹ thuật của Face Compare hoặc Liveness -> TECHNICAL_FAILURE
        FaceCompareStatus faceStatus = providerResult.getFaceCompareStatus() != null
                ? providerResult.getFaceCompareStatus()
                : FaceCompareStatus.MISSING;
        if (faceStatus == FaceCompareStatus.MATCH && (providerResult.getFaceMatchScore() == null
                || !Double.isFinite(providerResult.getFaceMatchScore()) || providerResult.getFaceMatchScore() < 0)) {
            faceStatus = FaceCompareStatus.INVALID_FORMAT;
            flags.put("faceCompareStatus", faceStatus.name());
        }
        if (faceStatus == FaceCompareStatus.MISSING
                || faceStatus == FaceCompareStatus.TIMEOUT
                || faceStatus == FaceCompareStatus.INVALID_FORMAT
                || faceStatus == FaceCompareStatus.ERROR) {
            String errorCode = "VNPT_FACE_COMPARE_" + faceStatus.name();
            flags.put("technicalFailure", true);
            flags.put("errorCode", errorCode);
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.TECHNICAL_FAILURE)
                    .routingDecision(EkycRoutingDecision.TECHNICAL_FAILURE)
                    .routingReason("Không nhận được kết quả so khớp khuôn mặt hợp lệ từ VNPT eKYC. Vui lòng thử lại sau.")
                    .failureCode(errorCode)
                    .retryable(true)
                    .fraudFlags(flags)
                    .build();
        }

        LivenessStatus livenessStatus = providerResult.getLivenessStatus() != null
                ? providerResult.getLivenessStatus()
                : LivenessStatus.NOT_EVALUATED;
        if (livenessStatus == LivenessStatus.ERROR) {
            flags.put("technicalFailure", true);
            flags.put("errorCode", "VNPT_LIVENESS_ERROR");
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.TECHNICAL_FAILURE)
                    .routingDecision(EkycRoutingDecision.TECHNICAL_FAILURE)
                    .routingReason("Không thể hoàn tất kiểm tra sinh trắc học Liveness từ VNPT eKYC. Vui lòng thử lại sau.")
                    .failureCode("VNPT_LIVENESS_ERROR")
                    .retryable(true)
                    .fraudFlags(flags)
                    .build();
        }

        // 4. Các lỗi nghiệp vụ / gian lận rõ ràng -> REJECTED
        if (!providerResult.isDocumentLegal()
                || providerResult.isFakeDocument()
                || providerResult.isPhotoshoped()
                || providerResult.isScreenCaptured()
                || providerResult.isCutCorner()
                || providerResult.isBackCardTampered()) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Phát hiện giấy tờ CCCD có dấu hiệu bị chỉnh sửa, cắt góc, chụp lại màn hình hoặc không hợp lệ (VNPT Anti-fraud).")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (livenessStatus == LivenessStatus.FAILED) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Ảnh xác minh không vượt qua kiểm tra sinh trắc học thực thể sống (Liveness/PAD). Vui lòng chụp trực tiếp giấy tờ gốc và khuôn mặt thật.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (providerResult.getExpiryStatus() == ExpiryDateStatus.EXPIRED || providerResult.isExpired()) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Thẻ CCCD đã hết hạn sử dụng. Vui lòng sử dụng giấy tờ tùy thân còn hiệu lực.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (faceStatus == FaceCompareStatus.NOMATCH) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Khuôn mặt trong ảnh selfie không trùng khớp với ảnh chân dung trên thẻ CCCD.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (isDuplicateCccd) {
            flags.put("duplicateCccd", true);
            String masked = cryptoService.maskCccd(providerResult.getIdNumber());
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Số Căn cước công dân (" + (masked != null ? masked : "này")
                            + ") đã được sử dụng để xác thực cho một tài khoản khác trong hệ thống. Mỗi CCCD chỉ được định danh cho duy nhất 1 tài khoản.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        // 5. Thiếu dữ liệu OCR, không rõ ràng, chưa đọc được ngày hết hạn hoặc không đạt điều kiện tự động -> REJECTED (không chuyển hàng đợi Admin)
        String normalizedCccd = cryptoService.normalizeCccd(providerResult.getIdNumber());
        if (!cryptoService.isValidCccdFormat(normalizedCccd)
                || providerResult.getFullName() == null
                || providerResult.getFullName().isBlank()) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Không đọc đủ số CCCD hoặc họ tên từ ảnh mặt trước. Vui lòng chụp lại ảnh CCCD rõ nét, không chói sáng và nộp lại.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (vnptProperties != null && vnptProperties.isVerifyBackCard() && !providerResult.isBackCardVerified()) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Chưa xác minh được đầy đủ mặt sau CCCD. Vui lòng chụp lại ảnh mặt sau rõ nét, đủ góc cạnh và nộp lại.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (providerResult.getExpiryStatus() != ExpiryDateStatus.VALID) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Không đọc được ngày hết hạn trên thẻ CCCD để xác nhận hiệu lực. Vui lòng chụp lại ảnh mặt trước rõ nét và nộp lại.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (faceStatus == FaceCompareStatus.BELOW_THRESHOLD) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Độ khớp khuôn mặt giữa ảnh chân dung và CCCD chưa đạt ngưỡng xác thực tự động. Vui lòng chụp lại ảnh selfie đủ sáng, chính diện và nộp lại.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (vnptProperties != null && vnptProperties.isRequireLivenessForAutoApprove() && livenessStatus != LivenessStatus.PASSED) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Chưa xác nhận được ảnh chân dung trực tiếp (Liveness/PAD). Vui lòng chụp lại ảnh selfie rõ khuôn mặt và nộp lại.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (providerResult.isHasBlurryWarning()) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Ảnh CCCD bị mờ, nhòe hoặc chói sáng nên chưa đủ dữ liệu đối chiếu. Vui lòng chụp lại ảnh rõ nét và nộp lại.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        if (!mismatchFields.isEmpty()) {
            return DecisionOutcome.builder()
                    .status(EkycVerificationStatus.REJECTED)
                    .routingDecision(EkycRoutingDecision.REJECTED)
                    .routingReason("Thông tin đối chiếu (" + String.join(", ", mismatchFields)
                            + ") không trùng khớp với dữ liệu VNPT đọc từ CCCD. Vui lòng kiểm tra lại thông tin và chụp lại ảnh giấy tờ.")
                    .retryable(false)
                    .fraudFlags(flags)
                    .build();
        }

        // 6. Đáp ứng 100% điều kiện xác minh -> AUTO_APPROVED
        return DecisionOutcome.builder()
                .status(EkycVerificationStatus.APPROVED)
                .routingDecision(EkycRoutingDecision.AUTO_APPROVED)
                .routingReason("VNPT eKYC xác thực giấy tờ CCCD và so khớp khuôn mặt thành công.")
                .retryable(false)
                .fraudFlags(flags)
                .build();
    }

    private List<String> crossCheckUserInput(EkycIdentityResultDto providerResult, EkycUserSubmittedInput userInput) {
        if (userInput == null || !userInput.hasAnyData() || providerResult == null) {
            return List.of();
        }
        List<String> mismatches = new ArrayList<>();

        String submittedCccd = cryptoService.normalizeCccd(userInput.getCccdNumber());
        String verifiedCccd = cryptoService.normalizeCccd(providerResult.getIdNumber());
        if (submittedCccd != null && verifiedCccd != null && !submittedCccd.equals(verifiedCccd)) {
            mismatches.add("cccdNumber");
        }

        if (userInput.getFullName() != null && !userInput.getFullName().isBlank()
                && providerResult.getFullName() != null && !providerResult.getFullName().isBlank()) {
            String submittedName = normalizeVietnameseText(userInput.getFullName());
            String verifiedName = normalizeVietnameseText(providerResult.getFullName());
            if (!submittedName.equals(verifiedName)) {
                mismatches.add("fullName");
            }
        }

        return mismatches;
    }

    private static String normalizeVietnameseText(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return normalized.trim();
    }
}
