package com.cani.dto.skill;

import com.cani.entity.enums.ExperienceLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for Worker Self-Declared Skills (Tier 1, UC-32 & UC-42)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclareSkillRequestDto {

    @NotEmpty(message = "Danh sách kỹ năng khai báo không được để trống")
    @Valid
    private List<SkillItem> skills;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillItem {
        @NotNull(message = "skillId không được để trống")
        private UUID skillId;

        @Builder.Default
        private ExperienceLevel proficiency = ExperienceLevel.BEGINNER;
    }
}
