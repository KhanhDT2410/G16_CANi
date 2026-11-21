package com.cani.service.skill;

import com.cani.dto.skill.SkillVerificationAiResultDto;
import com.cani.dto.skill.TrustScoreBreakdownDto;
import com.cani.entity.Skill;
import com.cani.entity.enums.DocumentAuthenticityStatus;
import com.cani.entity.enums.IdentityVerificationStatus;
import com.cani.entity.enums.IssuerVerificationStatus;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillVerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CANi Skill Trust & Verification Engine (V2).
 * Tính Trust Score độc lập từ 4 thành phần (0-100) và áp dụng các Hard Gates
 * để Backend kiểm soát tuyệt đối quyết định nghiệp vụ cuối cùng.
 */
@Component
@RequiredArgsConstructor
public class SkillTrustScoringEngine {

    public static final double WEIGHT_IDENTITY = 0.30;
    public static final double WEIGHT_AUTHENTICITY = 0.30;
    public static final double WEIGHT_SKILL_RELEVANCE = 0.25;
    public static final double WEIGHT_ISSUER = 0.15;

    public static final double AUTO_APPROVE_TRUST_THRESHOLD = 85.0;
    public static final double AUTO_APPROVE_CONFIDENCE_THRESHOLD = 0.80;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Set<String> NON_CERTIFICATE_DOC_TYPES = Set.of(
            "CV", "RESUME", "SELFIE", "IRRELEVANT", "MEME", "INVOICE", "PORTFOLIO"
    );
    private static final Set<String> VALID_CERTIFICATE_DOC_TYPES = Set.of(
            "CERTIFICATE", "DIPLOMA", "LICENSE"
    );

    private final AiOutputConsistencyValidator consistencyValidator;
    private final TrustedIssuerRegistry trustedIssuerRegistry;

    public TrustEvaluationResult evaluate(
            String workerFullName,
            Skill requestedSkill,
            List<Skill> activeSkills,
            SkillVerificationAiResultDto aiResult
    ) {
        AiOutputConsistencyValidator.ValidatedAiOutput validated =
                consistencyValidator.validate(aiResult, activeSkills);

        Set<String> riskFlags = new LinkedHashSet<>(validated.getNormalizedRiskFlags());
        List<String> hardGates = new ArrayList<>();

        // 1. Identity Verification (0-100)
        IdentityResult identity = evaluateIdentity(workerFullName, aiResult.getRecipientName(), aiResult.getIdentityMatch());
        if (identity.status == IdentityVerificationStatus.MISMATCH) {
            riskFlags.add("NAME_MISMATCH");
            hardGates.add("HG-03_IDENTITY_MISMATCH");
        } else if (identity.status == IdentityVerificationStatus.UNVERIFIED) {
            hardGates.add("HG-06_MISSING_OR_UNREADABLE_IDENTITY");
        }

        // 2. Issuer Verification (0-100)
        TrustedIssuerRegistry.IssuerAssessment issuer = trustedIssuerRegistry.assess(
                aiResult.getInstitution(),
                aiResult.getVerificationUrl(),
                aiResult.getCertificateNumber(),
                Boolean.TRUE.equals(aiResult.getHasVerificationQr())
        );
        if (issuer.isUnofficialUrlDetected()) {
            riskFlags.add("UNOFFICIAL_VERIFICATION_URL");
        }

        // 3. Document Authenticity (0-100)
        AuthenticityResult auth = evaluateAuthenticity(aiResult, validated, riskFlags);
        if (auth.status == DocumentAuthenticityStatus.IRRELEVANT_DOCUMENT) {
            hardGates.add("HG-02_IRRELEVANT_OR_CV_DOCUMENT");
        } else if (auth.status == DocumentAuthenticityStatus.TAMPERED) {
            hardGates.add("HG-04_DOCUMENT_TAMPERED");
        } else if (auth.status == DocumentAuthenticityStatus.UNREADABLE) {
            hardGates.add("HG-06_UNREADABLE_DOCUMENT");
        } else if (auth.status == DocumentAuthenticityStatus.SUSPICIOUS) {
            hardGates.add("HG-09_SUSPICIOUS_AUTHENTICITY");
        }

        // Date hard gates
        if (validated.isCertificateExpired()) {
            hardGates.add("HG-05_EXPIRED_CERTIFICATE");
        }
        if (validated.isIssueDateInFuture() || validated.isExpiryBeforeIssueDate()) {
            hardGates.add("HG-05B_DATE_INCONSISTENCY");
        }
        if (validated.isConfidenceOutOfRange()) {
            hardGates.add("HG-09_INVALID_AI_CONFIDENCE");
        }

        // 4. Skill Relevance (0-100)
        SkillRelevanceResult relevance = evaluateSkillRelevance(requestedSkill, validated, aiResult.getSkillRelevance());
        if (relevance.score < 40) {
            hardGates.add("HG-08_NO_SKILL_MATCH");
        } else if (relevance.score < 70) {
            hardGates.add("HG-08_PARTIAL_SKILL_RELEVANCE");
        }

        // Authority & Issuer hard gate (prevents auto-approve without trusted issuer or authority evidence)
        boolean hasAnyAuthorityEvidence = validated.isHasSealOrSignature()
                || Boolean.TRUE.equals(aiResult.getHasVerificationQr())
                || issuer.getStatus() == IssuerVerificationStatus.VERIFIED_ONLINE;

        if (!hasAnyAuthorityEvidence
                || issuer.getStatus() == IssuerVerificationStatus.UNVERIFIED
                || issuer.getStatus() == IssuerVerificationStatus.UNKNOWN_ISSUER) {
            hardGates.add("HG-07_UNVERIFIED_ISSUER_OR_MISSING_AUTHORITY");
        }

        // 5. Weighted Trust Score (0.00 - 100.00)
        double rawWeighted = (WEIGHT_IDENTITY * identity.score)
                + (WEIGHT_AUTHENTICITY * auth.score)
                + (WEIGHT_SKILL_RELEVANCE * relevance.score)
                + (WEIGHT_ISSUER * issuer.getScore());
        double trustScore = BigDecimal.valueOf(rawWeighted)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        // 6. Authoritative Routing & Status Decision (Hard Gates override score)
        DecisionOutcome decision = resolveFinalDecision(
                trustScore,
                validated,
                identity,
                issuer,
                auth,
                relevance,
                hardGates,
                aiResult,
                workerFullName
        );

        String explanation = buildExplanation(identity, auth, relevance, issuer, trustScore, hardGates, decision.status);

        TrustScoreBreakdownDto breakdown = TrustScoreBreakdownDto.builder()
                .identityScore(identity.score)
                .documentAuthenticityScore(auth.score)
                .skillRelevanceScore(relevance.score)
                .issuerVerificationScore(issuer.getScore())
                .weightedTrustScore(trustScore)
                .hardGatesTriggered(hardGates)
                .riskFlags(new ArrayList<>(riskFlags))
                .missingInformation(validated.getMissingInformation())
                .explanation(explanation)
                .build();

        return TrustEvaluationResult.builder()
                .trustScore(trustScore)
                .breakdown(breakdown)
                .identityStatus(identity.status)
                .issuerStatus(issuer.getStatus())
                .authenticityStatus(auth.status)
                .verificationSource(issuer.getVerificationSource())
                .verificationReference(issuer.getVerificationReference())
                .finalRoutingDecision(decision.routingDecision)
                .finalStatus(decision.status)
                .finalReason(decision.reason)
                .matchedApprovedSkills(relevance.approvedSkills)
                .sanitizedConfidence(validated.getSanitizedConfidence())
                .parsedExpiryDate(validated.getParsedExpiryDate())
                .build();
    }

    private record IdentityResult(IdentityVerificationStatus status, int score) {}
    private record AuthenticityResult(DocumentAuthenticityStatus status, int score) {}
    private record SkillRelevanceResult(int score, List<Skill> approvedSkills) {}
    private record DecisionOutcome(RoutingDecision routingDecision, SkillVerificationStatus status, String reason) {}

    IdentityResult evaluateIdentity(String workerFullName, String recipientName, String aiIdentityMatch) {
        if (recipientName == null || recipientName.isBlank()) {
            return new IdentityResult(IdentityVerificationStatus.UNVERIFIED, 40);
        }
        if (workerFullName == null || workerFullName.isBlank()) {
            return new IdentityResult(IdentityVerificationStatus.UNVERIFIED, 50);
        }

        String normWorker = normalizeName(workerFullName);
        String normRecipient = normalizeName(recipientName);

        if (normWorker.equals(normRecipient)) {
            return new IdentityResult(IdentityVerificationStatus.EXACT_MATCH, 100);
        }

        String[] wTokens = normWorker.split("\\s+");
        String[] rTokens = normRecipient.split("\\s+");

        // Cùng tập token nhưng đảo thứ tự (vd: "Hung Pham Duy" vs "Pham Duy Hung")
        if (wTokens.length == rTokens.length && wTokens.length >= 2) {
            List<String> wSorted = Arrays.stream(wTokens).sorted().toList();
            List<String> rSorted = Arrays.stream(rTokens).sorted().toList();
            if (wSorted.equals(rSorted)) {
                return new IdentityResult(IdentityVerificationStatus.EXACT_MATCH, 95);
            }
        }

        // So khớp nghiêm ngặt hơn V1: phải trùng họ & tên chính, và kiểm tra tên đệm không mâu thuẫn
        if (wTokens.length >= 2 && rTokens.length >= 2) {
            boolean firstMatches = wTokens[0].equals(rTokens[0]);
            boolean lastMatches = wTokens[wTokens.length - 1].equals(rTokens[rTokens.length - 1]);

            if (firstMatches && lastMatches) {
                List<String> wMid = middleTokens(wTokens);
                List<String> rMid = middleTokens(rTokens);

                // Một bên lược bỏ tên đệm (vd "Phạm Hưng" vs "Phạm Duy Hưng")
                if (wMid.isEmpty() || rMid.isEmpty()) {
                    if ("MISMATCH".equalsIgnoreCase(aiIdentityMatch)) {
                        return new IdentityResult(IdentityVerificationStatus.PARTIAL_MATCH, 60);
                    }
                    return new IdentityResult(IdentityVerificationStatus.PARTIAL_MATCH, 80);
                }

                // Kiểm tra tên đệm viết tắt khớp chữ cái đầu (vd "Pham D. Hung" vs "Pham Duy Hung")
                if (isMiddleInitialMatch(wMid, rMid)) {
                    return new IdentityResult(IdentityVerificationStatus.PARTIAL_MATCH, 85);
                }

                // Hai bên đều có tên đệm nhưng KHÁC NHAU (vd "Nguyễn Văn An" vs "Nguyễn Thị An") -> MISMATCH!
                return new IdentityResult(IdentityVerificationStatus.MISMATCH, 0);
            }
        }

        return new IdentityResult(IdentityVerificationStatus.MISMATCH, 0);
    }

    private AuthenticityResult evaluateAuthenticity(
            SkillVerificationAiResultDto aiResult,
            AiOutputConsistencyValidator.ValidatedAiOutput validated,
            Set<String> riskFlags
    ) {
        String docType = aiResult.getDocumentType() != null
                ? aiResult.getDocumentType().trim().toUpperCase(Locale.ROOT)
                : "";
        String aiAuth = aiResult.getDocumentAuthenticity() != null
                ? aiResult.getDocumentAuthenticity().trim().toUpperCase(Locale.ROOT)
                : "";

        if (NON_CERTIFICATE_DOC_TYPES.contains(docType)
                || riskFlags.contains("CV_DOCUMENT")
                || riskFlags.contains("IRRELEVANT_DOCUMENT")) {
            return new AuthenticityResult(DocumentAuthenticityStatus.IRRELEVANT_DOCUMENT, 0);
        }

        if ("TAMPERED".equals(aiAuth)
                || riskFlags.contains("SUSPECTED_TAMPERING")
                || riskFlags.contains("FORGED_DOCUMENT")) {
            return new AuthenticityResult(DocumentAuthenticityStatus.TAMPERED, 0);
        }

        if (Boolean.FALSE.equals(aiResult.getDocumentReadable()) || riskFlags.contains("UNREADABLE_IMAGE")) {
            return new AuthenticityResult(DocumentAuthenticityStatus.UNREADABLE, 35);
        }

        if ("SUSPICIOUS".equals(aiAuth)
                || riskFlags.contains("PROMPT_INJECTION_ATTEMPT")
                || riskFlags.contains("SCREENSHOT_ARTIFACT")
                || validated.isIssueDateInFuture()
                || validated.isExpiryBeforeIssueDate()) {
            return new AuthenticityResult(DocumentAuthenticityStatus.SUSPICIOUS, 30);
        }

        int score = 0;
        if (VALID_CERTIFICATE_DOC_TYPES.contains(docType) || docType.isBlank()) {
            score += 40;
        } else {
            score += 15;
        }

        boolean hasDigitalProof = Boolean.TRUE.equals(aiResult.getHasVerificationQr())
                || Boolean.TRUE.equals(aiResult.getHasVerificationUrl());
        if (validated.isHasSealOrSignature() && hasDigitalProof) {
            score += 30;
        } else if (validated.isHasSealOrSignature()) {
            score += 25;
        } else if (hasDigitalProof) {
            score += 20;
        }

        if (aiResult.getInstitution() != null && !aiResult.getInstitution().isBlank()) {
            score += 10;
        }
        if (aiResult.getIssueDate() != null && !aiResult.getIssueDate().isBlank()) {
            score += 10;
        }
        if (aiResult.getCertificateNumber() != null && !aiResult.getCertificateNumber().isBlank()) {
            score += 5;
        }
        if (validated.getSanitizedConfidence() >= 0.80 && riskFlags.isEmpty()) {
            score += 10;
        } else if (validated.getSanitizedConfidence() >= 0.65) {
            score += 5;
        }

        if (riskFlags.contains("BLURRY_IMAGE")) {
            score = Math.max(0, score - 25);
        }

        score = Math.min(100, score);
        DocumentAuthenticityStatus status = score >= 75
                ? DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT
                : DocumentAuthenticityStatus.UNVERIFIED;

        return new AuthenticityResult(status, score);
    }

    private SkillRelevanceResult evaluateSkillRelevance(
            Skill requestedSkill,
            AiOutputConsistencyValidator.ValidatedAiOutput validated,
            String aiSkillRelevance
    ) {
        List<Skill> matched = new ArrayList<>(validated.getMatchedTaxonomySkills());

        if ("NO_MATCH".equalsIgnoreCase(aiSkillRelevance) && matched.isEmpty()) {
            return new SkillRelevanceResult(0, Collections.emptyList());
        }

        if (requestedSkill != null) {
            boolean directMatch = matched.stream()
                    .anyMatch(s -> s.getSkillId().equals(requestedSkill.getSkillId()));
            if (directMatch) {
                int score = validated.getMaxMatchedSkillConfidence() >= 0.75 ? 100 : 75;
                return new SkillRelevanceResult(score, matched);
            }
            if (!matched.isEmpty()) {
                // Chứng chỉ hợp lệ cho kỹ năng khác với kỹ năng Worker chọn -> cần Admin đối chiếu
                return new SkillRelevanceResult(50, matched);
            }
            return new SkillRelevanceResult(0, Collections.emptyList());
        }

        if (!matched.isEmpty()) {
            int score;
            if (validated.getMaxMatchedSkillConfidence() >= 0.85) {
                score = 100;
            } else if (validated.getMaxMatchedSkillConfidence() >= 0.75) {
                score = 90;
            } else {
                score = 65;
            }
            return new SkillRelevanceResult(score, matched);
        }

        return new SkillRelevanceResult(0, Collections.emptyList());
    }

    private DecisionOutcome resolveFinalDecision(
            double trustScore,
            AiOutputConsistencyValidator.ValidatedAiOutput validated,
            IdentityResult identity,
            TrustedIssuerRegistry.IssuerAssessment issuer,
            AuthenticityResult auth,
            SkillRelevanceResult relevance,
            List<String> hardGates,
            SkillVerificationAiResultDto aiResult,
            String workerFullName
    ) {
        // 1. Hard Reject Gates (tuyệt đối không để điểm thành phần khác bù trừ)
        if (hardGates.contains("HG-02_IRRELEVANT_OR_CV_DOCUMENT")) {
            String reason = nonBlankOr(aiResult.getRoutingReason(),
                    "Tài liệu tải lên không phải chứng chỉ/bằng cấp nghề nghiệp hợp lệ (CV tự khai, ảnh cá nhân hoặc tài liệu không liên quan). Vui lòng nộp Giấy chứng nhận/Bằng cấp chính quy.");
            return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, reason);
        }

        if (hardGates.contains("HG-03_IDENTITY_MISMATCH")) {
            String reason = "Họ tên trên chứng chỉ (" + safeStr(aiResult.getRecipientName())
                    + ") không trùng khớp với tên tài khoản của bạn (" + safeStr(workerFullName)
                    + "). Vui lòng nộp chứng chỉ chính chủ.";
            return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, reason);
        }

        if (hardGates.contains("HG-04_DOCUMENT_TAMPERED")) {
            String reason = nonBlankOr(aiResult.getRoutingReason(),
                    "Phát hiện dấu hiệu chỉnh sửa hoặc giả mạo bất thường trên tài liệu. Hồ sơ bị từ chối xác minh.");
            return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, reason);
        }

        if (hardGates.contains("HG-05_EXPIRED_CERTIFICATE")) {
            String reason = "Chứng chỉ đã hết hạn sử dụng (ngày hết hạn: " + safeStr(aiResult.getExpiryDate())
                    + "). Vui lòng nộp chứng chỉ còn hiệu lực.";
            return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, reason);
        }

        // 2. Gate kiểm tra chất lượng ảnh và tính toàn vẹn (Reject trực tiếp kèm hướng dẫn hành động, không bắt Admin duyệt tay)
        if (hardGates.contains("HG-06_UNREADABLE_DOCUMENT") || hardGates.contains("HG-06_MISSING_OR_UNREADABLE_IDENTITY")) {
            String reason = nonBlankOr(aiResult.getRoutingReason(),
                    "Ảnh tài liệu bị mờ hoặc không thể hiện rõ họ tên người được cấp. Vui lòng chụp lại bản gốc rõ nét, đủ ánh sáng để hệ thống tự động xác nhận ngay.");
            return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, reason);
        }

        if (hardGates.contains("HG-05B_DATE_INCONSISTENCY")
                || hardGates.contains("HG-09_SUSPICIOUS_AUTHENTICITY")
                || hardGates.contains("HG-09_INVALID_AI_CONFIDENCE")) {
            String reason = nonBlankOr(aiResult.getRoutingReason(),
                    "Tài liệu có thông tin ngày cấp/hết hạn bất thường hoặc không hợp lệ. Vui lòng nộp chứng chỉ chính quy còn hiệu lực để được duyệt tự động.");
            return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, reason);
        }

        if (hardGates.contains("HG-08_NO_SKILL_MATCH")) {
            String reason = nonBlankOr(aiResult.getRoutingReason(),
                    "Nội dung chứng chỉ không khớp với kỹ năng trong danh mục hoặc không khớp kỹ năng bạn đã đăng ký. Vui lòng nộp chứng chỉ đúng chuyên ngành.");
            return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, reason);
        }

        // 3. Auto-Approve Gate: Bắt buộc vượt qua MỌI điều kiện khắt khe
        boolean issuerEligibleForAuto = issuer.getStatus() == IssuerVerificationStatus.VERIFIED_ONLINE
                || issuer.getStatus() == IssuerVerificationStatus.KNOWN_ISSUER_UNVERIFIED_ONLINE;

        boolean canAutoApprove = hardGates.isEmpty()
                && trustScore >= AUTO_APPROVE_TRUST_THRESHOLD
                && validated.getSanitizedConfidence() >= AUTO_APPROVE_CONFIDENCE_THRESHOLD
                && identity.score >= 80
                && issuerEligibleForAuto
                && (aiResult.getRoutingDecision() == RoutingDecision.AUTO_APPROVE
                        || aiResult.getRoutingDecision() == RoutingDecision.ADMIN_REVIEW);

        if (canAutoApprove) {
            String issuerNote = issuer.getStatus() == IssuerVerificationStatus.VERIFIED_ONLINE
                    ? "Đã đối chiếu tên miền xác minh chính thức."
                    : "Tổ chức nằm trong danh mục uy tín nội bộ (chưa đối soát API trực tuyến).";
            String reason = nonBlankOr(aiResult.getRoutingReason(),
                    "Chứng chỉ đạt tiêu chuẩn xác minh tự động (Trust Score: " + trustScore + "/100). " + issuerNote);
            return new DecisionOutcome(RoutingDecision.AUTO_APPROVE, SkillVerificationStatus.APPROVED, reason);
        }

        // 4. Nếu không đủ điều kiện tự động duyệt -> REJECT trực tiếp với hướng dẫn hành động cụ thể cho Worker
        // Tuyệt đối KHÔNG đẩy vào hàng đợi Admin để tránh nghẽn luồng và không phụ thuộc Admin duyệt tay
        String rejectReason = buildActionableRejectReason(issuer, hardGates, trustScore, aiResult.getRoutingReason());
        return new DecisionOutcome(RoutingDecision.REJECT, SkillVerificationStatus.REJECTED, rejectReason);
    }

    private String buildActionableRejectReason(
            TrustedIssuerRegistry.IssuerAssessment issuer,
            List<String> hardGates,
            double trustScore,
            String aiReason
    ) {
        if (hardGates.contains("HG-07_UNVERIFIED_ISSUER_OR_MISSING_AUTHORITY")) {
            return "Tài liệu chưa đạt tiêu chuẩn tự động xác thực: Thiếu con dấu mộc, chữ ký hoặc tổ chức cấp chưa được xác minh. "
                    + "Vui lòng nộp chứng chỉ chính quy có dấu mộc đỏ hoặc mã QR tra cứu để được duyệt tự động ngay.";
        }
        if (hardGates.contains("HG-08_PARTIAL_SKILL_RELEVANCE")) {
            return "Nội dung chứng chỉ chỉ thể hiện một phần kỹ năng được yêu cầu. Vui lòng bổ sung chứng chỉ chuyên sâu phù hợp.";
        }
        if (trustScore < AUTO_APPROVE_TRUST_THRESHOLD) {
            return "Tài liệu chưa đạt ngưỡng độ tin cậy để tự động xác nhận (Trust Score: " + trustScore + "/100, yêu cầu tối thiểu " + (int) AUTO_APPROVE_TRUST_THRESHOLD + "). "
                    + "Vui lòng chụp lại bản gốc rõ nét và đầy đủ thông tin con dấu/tổ chức cấp.";
        }
        return nonBlankOr(aiReason,
                "Tài liệu chưa đạt tiêu chuẩn tự động xác thực của hệ thống. Vui lòng chụp lại bản gốc rõ nét để thử lại.");
    }

    private String buildExplanation(
            IdentityResult identity,
            AuthenticityResult auth,
            SkillRelevanceResult relevance,
            TrustedIssuerRegistry.IssuerAssessment issuer,
            double trustScore,
            List<String> hardGates,
            SkillVerificationStatus finalStatus
    ) {
        String gateStr = hardGates.isEmpty() ? "NONE" : String.join(", ", hardGates);
        return String.format(
                "TrustScore=%.2f [Identity=%d(%s)*0.30, Authenticity=%d(%s)*0.30, Relevance=%d*0.25, Issuer=%d(%s)*0.15] | HardGates=[%s] -> %s",
                trustScore,
                identity.score, identity.status,
                auth.score, auth.status,
                relevance.score,
                issuer.getScore(), issuer.getStatus(),
                gateStr,
                finalStatus
        );
    }

    private List<String> middleTokens(String[] tokens) {
        if (tokens.length <= 2) {
            return Collections.emptyList();
        }
        List<String> mids = new ArrayList<>();
        for (int i = 1; i < tokens.length - 1; i++) {
            mids.add(tokens[i]);
        }
        return mids;
    }

    private boolean isMiddleInitialMatch(List<String> wMid, List<String> rMid) {
        if (wMid.size() != rMid.size()) {
            return false;
        }
        for (int i = 0; i < wMid.size(); i++) {
            String a = wMid.get(i).replace(".", "");
            String b = rMid.get(i).replace(".", "");
            if (a.equals(b)) {
                continue;
            }
            if ((a.length() == 1 && b.startsWith(a)) || (b.length() == 1 && a.startsWith(b))) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String nfd = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String noDiacritics = DIACRITICS.matcher(nfd).replaceAll("").replace('đ', 'd');
        return noDiacritics.replaceAll("[^a-z0-9\\s.]", " ").replaceAll("\\s+", " ").trim();
    }

    private String nonBlankOr(String candidate, String fallback) {
        return (candidate != null && !candidate.isBlank()) ? candidate.trim() : fallback;
    }

    private String safeStr(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : "không rõ";
    }
}
