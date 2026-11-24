package com.cani.client.skill;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Port xác thực chứng chỉ nghề nghiệp từ nguồn tra cứu tin cậy thực tế
 * (QR dẫn tới trang tra cứu chính thức, API đơn vị cấp, hoặc chữ ký số hợp lệ).
 */
public interface CertificateLookupSource {

    CertificateLookupResult verifyCertificate(CertificateLookupRequest request);

    enum CertificateLookupOutcome {
        VERIFIED,
        MISMATCH,
        UNSUPPORTED_SOURCE,
        TEMPORARY_FAILURE
    }

    @Value
    @Builder
    class CertificateLookupRequest {
        UUID verificationId;
        UUID workerId;
        String workerFullName;
        String documentName;
        String mimeType;
        String sha256Hash;
        byte[] fileBytes;
        UUID requestedSkillId;
        String requestedSkillName;
        String notes;
        String extractedDocumentType;
        String extractedRecipientName;
        String extractedInstitution;
        String extractedCertificateNumber;
        String extractedExpiryDate;
        List<String> extractedSkills;
    }

    @Value
    @Builder
    class CertificateLookupResult {
        CertificateLookupOutcome outcome;
        String verificationSource;
        String verificationReference;
        String recipientName;
        String institution;
        String certificateNumber;
        String issueDate;
        String expiryDate;
        Boolean permanentExpiry;
        List<String> matchedSkillNames;
        String failureCode;
        String reason;

        public boolean isPermanent() {
            return Boolean.TRUE.equals(permanentExpiry);
        }
    }
}
