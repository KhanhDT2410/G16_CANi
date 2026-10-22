package com.cani.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedSkillDto {
    private String skillName;
    private String taxonomyRole;
    private Double confidenceScore;
    private String evidenceSnippet;
}
