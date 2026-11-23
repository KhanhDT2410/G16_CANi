package com.cani.service.ekyc;

import com.cani.dto.ekyc.EkycConsentPolicyDto;
import com.cani.entity.EkycConsentRecord;
import com.cani.entity.SystemConfig;
import com.cani.exception.BadRequestException;
import com.cani.repository.EkycConsentRecordRepository;
import com.cani.repository.SystemConfigRepository;
import com.cani.service.ekyc.storage.EkycImageStorage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Helper dùng chung cho Worker và EO eKYC để:
 * - Xác thực và lưu trữ bằng chứng đồng ý xử lý dữ liệu sinh trắc học theo Nghị định 13/2023/NĐ-CP.
 * - Đánh giá trạng thái đủ tuổi lao động tối thiểu (min_registration_age) từ ngày sinh OCR trong RAM,
 *   tránh lưu trữ hoặc phát tán ngày sinh thô khi không cần thiết.
 * - Kiểm tra tính sẵn sàng, hạn lưu trữ và khả năng đọc thực tế của bộ 3 ảnh xác minh.
 */
public final class EkycConsentAndAgeHelper {

    public static final int DEFAULT_MIN_REGISTRATION_AGE = 18;
    public static final String MIN_REGISTRATION_AGE_KEY = "min_registration_age";
    public static final String DEFAULT_CONSENT_NOTICE_VERSION = "2026.11-nd13-v1";
    public static final String CONSENT_TITLE =
            "Thông báo xử lý dữ liệu cá nhân & sinh trắc học (Nghị định 13/2023/NĐ-CP)";
    public static final String CONSENT_SUMMARY =
            "CANi chỉ sử dụng ảnh CCCD và ảnh chân dung selfie để xác thực danh tính qua nhà cung cấp VNPT eKYC, "
                    + "kiểm tra điều kiện tuổi tối thiểu và phòng chống trùng lặp tài khoản. "
                    + "Ảnh đối soát đã hoàn tất sẽ bị xóa tự động theo chính sách lưu trữ; "
                    + "trường hợp cần CSKH kiểm tra thủ công sẽ được lưu tạm tối đa 14 ngày.";

    private EkycConsentAndAgeHelper() {
    }

    public static EkycConsentPolicyDto buildConsentPolicy(boolean required, String noticeVersion) {
        String effectiveVersion = (noticeVersion != null && !noticeVersion.isBlank())
                ? noticeVersion.trim()
                : DEFAULT_CONSENT_NOTICE_VERSION;
        return EkycConsentPolicyDto.builder()
                .required(required)
                .noticeVersion(effectiveVersion)
                .title(CONSENT_TITLE)
                .summary(CONSENT_SUMMARY)
                .build();
    }

    public static String validateConsent(
            boolean consentRequired,
            String requiredNoticeVersion,
            Boolean consentAccepted,
            String submittedNoticeVersion) {

        String effectiveRequiredVersion = (requiredNoticeVersion != null && !requiredNoticeVersion.isBlank())
                ? requiredNoticeVersion.trim()
                : DEFAULT_CONSENT_NOTICE_VERSION;

        if (!consentRequired) {
            return effectiveRequiredVersion;
        }
        if (!Boolean.TRUE.equals(consentAccepted)) {
            throw new BadRequestException(
                    "Bạn cần xác nhận đồng ý Thông báo xử lý dữ liệu cá nhân & sinh trắc học (Nghị định 13/2023/NĐ-CP) trước khi gửi hồ sơ eKYC.");
        }
        if (submittedNoticeVersion != null && !submittedNoticeVersion.isBlank()
                && !effectiveRequiredVersion.equals(submittedNoticeVersion.trim())) {
            throw new BadRequestException(
                    "Phiên bản thông báo đồng ý xử lý dữ liệu eKYC không khớp ("
                            + submittedNoticeVersion.trim()
                            + "). Vui lòng tải lại thông báo mới nhất: " + effectiveRequiredVersion);
        }
        return effectiveRequiredVersion;
    }

    public static void persistConsentRecord(
            EkycConsentRecordRepository repository,
            UUID userId,
            String targetRole,
            String noticeVersion,
            Instant consentedAt) {
        if (repository == null || userId == null || noticeVersion == null || noticeVersion.isBlank()) {
            return;
        }
        repository.save(EkycConsentRecord.builder()
                .userId(userId)
                .targetRole(targetRole)
                .noticeVersion(noticeVersion.trim())
                .consentedAt(consentedAt != null ? consentedAt : Instant.now())
                .build());
    }

    public static int resolveMinRegistrationAge(SystemConfigRepository systemConfigRepository) {
        if (systemConfigRepository == null) {
            return DEFAULT_MIN_REGISTRATION_AGE;
        }
        try {
            return systemConfigRepository.findByKey(MIN_REGISTRATION_AGE_KEY)
                    .map(SystemConfig::getValue)
                    .map(String::trim)
                    .filter(val -> !val.isEmpty())
                    .map(Integer::parseInt)
                    .filter(age -> age > 0 && age < 100)
                    .orElse(DEFAULT_MIN_REGISTRATION_AGE);
        } catch (Exception ignored) {
            return DEFAULT_MIN_REGISTRATION_AGE;
        }
    }

    public static LocalDate parseDob(String rawDob) {
        if (rawDob == null || rawDob.isBlank()) {
            return null;
        }
        String trimmed = rawDob.trim();
        try {
            if (trimmed.contains("/")) {
                String[] parts = trimmed.split("/");
                if (parts.length == 3) {
                    if (parts[0].length() == 4) {
                        return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    }
                    return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                }
            } else if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-");
                if (parts.length == 3 && parts[0].length() != 4) {
                    return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                }
                return LocalDate.parse(trimmed);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public static Integer calculateAgeFromDob(String rawDob) {
        LocalDate birthDate = parseDob(rawDob);
        if (birthDate == null) {
            return null;
        }
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        return (age >= 0 && age <= 120) ? age : null;
    }

    public static Boolean evaluateMeetsMinimumAge(String rawDob, SystemConfigRepository systemConfigRepository) {
        Integer age = calculateAgeFromDob(rawDob);
        if (age == null) {
            return null;
        }
        return age >= resolveMinRegistrationAge(systemConfigRepository);
    }

    public static boolean isExpired(Instant retentionUntil, Instant purgedAt, Instant now) {
        if (purgedAt != null) {
            return true;
        }
        return retentionUntil != null && !retentionUntil.isAfter(now);
    }

    public static ImageAvailability evaluateImageAvailability(
            EkycImageStorage imageStorage,
            String frontKey,
            String backKey,
            String selfieKey,
            Instant retentionUntil,
            Instant purgedAt) {

        Instant now = Instant.now();
        if (purgedAt != null) {
            return new ImageAvailability(
                    false,
                    true,
                    false,
                    "Ảnh xác minh đã bị xóa sau khi hết hạn lưu trữ. Vui lòng từ chối và yêu cầu người dùng nộp lại hồ sơ.");
        }
        if (retentionUntil != null && !retentionUntil.isAfter(now)) {
            return new ImageAvailability(
                    false,
                    true,
                    false,
                    "Ảnh xác minh đã hết hạn lưu trữ tạm thời. Vui lòng từ chối và yêu cầu người dùng nộp lại hồ sơ.");
        }

        boolean hasFront = isManagedAndReadable(imageStorage, frontKey);
        boolean hasBack = isManagedAndReadable(imageStorage, backKey);
        boolean hasSelfie = isManagedAndReadable(imageStorage, selfieKey);
        boolean allThreeReadable = hasFront && hasBack && hasSelfie;
        if (!allThreeReadable) {
            return new ImageAvailability(
                    false,
                    false,
                    false,
                    "Thiếu hoặc không thể đọc đầy đủ 3 ảnh xác minh bắt buộc (Mặt trước, Mặt sau, Selfie). Không thể phê duyệt thủ công; vui lòng yêu cầu người dùng nộp lại.");
        }
        return new ImageAvailability(true, false, true, null);
    }

    private static boolean isManagedAndReadable(EkycImageStorage storage, String key) {
        return storage != null && storage.isManagedObjectKey(key) && storage.isReadable(key);
    }

    public record ImageAvailability(
            boolean available,
            boolean expired,
            boolean allThreePresentAndReadable,
            String unavailableReason
    ) {}
}
