package com.cani.dto.skill;

import com.cani.entity.enums.RoutingDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO chứa kết quả tầng Document Intelligence do Vision AI trích xuất (V2).
 * Chỉ phản ánh thông tin AI đọc được từ tài liệu; quyết định cuối cùng thuộc về Backend Trust Engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillVerificationAiResultDto {
    // --- Các trường tương thích ngược V1 ---
    private String recipientName;
    private String identityNumber;
    private String institution;
    private String issueDate;

    @Builder.Default
    private List<ExtractedSkillDto> extractedSkills = new ArrayList<>();

    @Builder.Default
    private Boolean hasSealOrSignature = false;

    @Builder.Default
    private Double overallConfidence = 0.0;

    private RoutingDecision routingDecision;
    private String routingReason;

    // --- Các trường mở rộng Document Intelligence V2 ---
    private String documentType;

    @Builder.Default
    private Boolean documentReadable = true;

    private String certificateNumber;
    private String expiryDate;
    private String verificationUrl;

    @Builder.Default
    private Boolean hasSeal = false;

    @Builder.Default
    private Boolean hasSignature = false;

    @Builder.Default
    private Boolean hasVerificationQr = false;

    @Builder.Default
    private Boolean hasVerificationUrl = false;

    private String identityMatch;
    private String documentAuthenticity;
    private String skillRelevance;

    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();

    @Builder.Default
    private List<String> missingInformation = new ArrayList<>();

    private String modelName;
    private String promptVersion;
    private String rawAiJson;
}
