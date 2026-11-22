package com.cani.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyAttendanceRequestDto {

    private UUID confirmedPerformedSkillId;

    private Boolean confirmPerformedSkill;
}
