package com.cani.service.skill;

import com.cani.dto.skill.ExtractedSkillDto;
import com.cani.dto.skill.SkillVerificationAiResultDto;
import com.cani.entity.Skill;
import lombok.Builder;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kiểm tra tính nhất quán của dữ liệu JSON do Vision AI trả về trước khi chấm Trust Score.
 * Backend tuyệt đối KHÔNG tin mù quáng vào JSON từ AI (Mục 4.4).
 */
@Component
public class AiOutputConsistencyValidator {

    private static final List<DateTimeFormatter> FULL_DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    private static final List<DateTimeFormatter> YEAR_MONTH_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM"),
            DateTimeFormatter.ofPattern("M/yyyy")
    );

    private final Clock clock;

    @Autowired
    public AiOutputConsistencyValidator() {
        this(Clock.systemUTC());
    }

    AiOutputConsistencyValidator(Clock clock) {
        this.clock = clock;
    }

    @Value
    @Builder
    public static class ValidatedAiOutput {
        double sanitizedConfidence;
        boolean confidenceOutOfRange;
        LocalDate parsedIssueDate;
        LocalDate parsedExpiryDate;
        boolean issueDateInFuture;
        boolean expiryBeforeIssueDate;
        boolean certificateExpired;
        boolean hasSealOrSignature;
        List<Skill> matchedTaxonomySkills;
        double maxMatchedSkillConfidence;
        Set<String> normalizedRiskFlags;
        List<String> missingInformation;
    }

    public ValidatedAiOutput validate(SkillVerificationAiResultDto aiResult, List<Skill> activeSkills) {
        Set<String> flags = new LinkedHashSet<>();
        if (aiResult.getRiskFlags() != null) {
            aiResult.getRiskFlags().stream()
                    .filter(Objects::nonNull)
                    .map(f -> f.trim().toUpperCase(Locale.ROOT))
                    .filter(f -> !f.isBlank())
                    .forEach(flags::add);
        }

        List<String> missing = new ArrayList<>();
        if (aiResult.getMissingInformation() != null) {
            aiResult.getMissingInformation().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(missing::add);
        }

        // 1. Validate confidence range [0.0, 1.0]
        Double rawConf = aiResult.getOverallConfidence();
        boolean confInvalid = (rawConf == null || rawConf.isNaN() || rawConf.isInfinite()
                || rawConf < 0.0 || rawConf > 1.0);
        double sanitizedConf;
        if (rawConf == null || rawConf.isNaN() || rawConf.isInfinite()) {
            sanitizedConf = 0.0;
        } else {
            sanitizedConf = Math.max(0.0, Math.min(1.0, rawConf));
        }
        if (confInvalid) {
            flags.add("INVALID_AI_CONFIDENCE");
        }

        // 2. Validate issueDate & expiryDate consistency
        LocalDate today = LocalDate.now(clock);
        LocalDate issueDate = parseFlexibleDate(aiResult.getIssueDate(), false);
        LocalDate expiryDate = parseFlexibleDate(aiResult.getExpiryDate(), true);

        boolean issueInFuture = issueDate != null && issueDate.isAfter(today.plusDays(1));
        if (issueInFuture) {
            flags.add("FUTURE_ISSUE_DATE");
        }

        boolean expiryBeforeIssue = (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate));
        if (expiryBeforeIssue) {
            flags.add("EXPIRY_BEFORE_ISSUE_DATE");
        }

        boolean expired = expiryDate != null && expiryDate.isBefore(today);
        if (expired) {
            flags.add("EXPIRED_CERTIFICATE");
        }

        // 3. Validate extractedSkills against active CANi Skill Taxonomy
        Map<String, Skill> taxonomyByName = (activeSkills == null ? List.<Skill>of() : activeSkills).stream()
                .filter(s -> s != null && s.getName() != null)
                .collect(Collectors.toMap(
                        s -> s.getName().trim().toLowerCase(Locale.ROOT),
                        s -> s,
                        (a, b) -> a
                ));

        List<Skill> matchedSkills = new ArrayList<>();
        double maxMatchedConf = 0.0;
        if (aiResult.getExtractedSkills() != null) {
            for (ExtractedSkillDto ext : aiResult.getExtractedSkills()) {
                if (ext == null) {
                    continue;
                }
                double skillConf = sanitizeUnitScore(ext.getConfidenceScore());
                ext.setConfidenceScore(skillConf);

                String role = ext.getTaxonomyRole() != null ? ext.getTaxonomyRole().trim() : "";
                if (!role.isBlank() && !"N/A".equalsIgnoreCase(role)) {
                    Skill matched = taxonomyByName.get(role.toLowerCase(Locale.ROOT));
                    if (matched != null) {
                        if (!matchedSkills.contains(matched)) {
                            matchedSkills.add(matched);
                        }
                        maxMatchedConf = Math.max(maxMatchedConf, skillConf);
                    }
                }
            }
        }

        boolean hasAuthoritySignal = Boolean.TRUE.equals(aiResult.getHasSealOrSignature())
                || Boolean.TRUE.equals(aiResult.getHasSeal())
                || Boolean.TRUE.equals(aiResult.getHasSignature());

        if (aiResult.getRecipientName() == null || aiResult.getRecipientName().isBlank()) {
            if (!missing.contains("recipientName")) {
                missing.add("recipientName");
            }
        }
        if (aiResult.getInstitution() == null || aiResult.getInstitution().isBlank()) {
            if (!missing.contains("issuerName")) {
                missing.add("issuerName");
            }
        }

        return ValidatedAiOutput.builder()
                .sanitizedConfidence(sanitizedConf)
                .confidenceOutOfRange(confInvalid)
                .parsedIssueDate(issueDate)
                .parsedExpiryDate(expiryDate)
                .issueDateInFuture(issueInFuture)
                .expiryBeforeIssueDate(expiryBeforeIssue)
                .certificateExpired(expired)
                .hasSealOrSignature(hasAuthoritySignal)
                .matchedTaxonomySkills(matchedSkills)
                .maxMatchedSkillConfidence(maxMatchedConf)
                .normalizedRiskFlags(flags)
                .missingInformation(missing)
                .build();
    }

    public LocalDate parseFlexibleDate(String rawDate, boolean endOfPeriod) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        String trimmed = rawDate.trim();
        for (DateTimeFormatter fmt : FULL_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        for (DateTimeFormatter fmt : YEAR_MONTH_FORMATTERS) {
            try {
                YearMonth ym = YearMonth.parse(trimmed, fmt);
                return endOfPeriod ? ym.atEndOfMonth() : ym.atDay(1);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        if (trimmed.matches("^\\d{4}$")) {
            try {
                Year y = Year.parse(trimmed);
                return endOfPeriod ? y.atMonth(12).atEndOfMonth() : y.atDay(1);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private double sanitizeUnitScore(Double val) {
        if (val == null || val.isNaN() || val.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, val));
    }
}
