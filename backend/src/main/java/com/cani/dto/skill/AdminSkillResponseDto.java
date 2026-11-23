package com.cani.dto.skill;

import com.cani.entity.Skill;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSkillResponseDto {

    private UUID skillId;
    private String name;
    private String category;
    private String description;
    private Boolean isActive;
    private Instant createdAt;

    public static AdminSkillResponseDto fromEntity(Skill skill) {
        if (skill == null) {
            return null;
        }
        return AdminSkillResponseDto.builder()
                .skillId(skill.getSkillId())
                .name(skill.getName())
                .category(skill.getCategory())
                .description(skill.getDescription())
                .isActive(skill.getIsActive() != null ? skill.getIsActive() : true)
                .createdAt(skill.getCreatedAt())
                .build();
    }
}
