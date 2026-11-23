package com.cani.dto.profile;

import com.cani.entity.enums.SkillTier;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerPublicProfileResponseDto {
    private UUID workerId;
    private UUID userId;
    private String fullName;
    private String avatarUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String phone;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    private String bio;
    private Integer yearsExperience;
    private Boolean isOnline;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer age;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String baseAddress;

    // Năng lực & Uy tín
    private SkillTier skillTier;
    private Integer totalSp;
    private Integer reliabilityScore;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer totalShiftsCompleted;
    private Integer totalCancellations;
    private Integer totalNoshows;

    // Danh sách kỹ năng (Phân loại Tier 1, Tier 2 tích xanh AI, Tier 3)
    private List<WorkerSkillProfileDto> skills;

    // Lịch sử đánh giá thực tế từ các nhà tổ chức sự kiện
    private List<WorkerReviewSummaryDto> reviews;

    // Quan hệ với EO đang tra cứu hồ sơ (nếu caller là EO)
    private Boolean isMyTeamMember;
    private Boolean isFavorited;
    private String internalNote;
}
