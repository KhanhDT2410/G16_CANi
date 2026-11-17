package com.cani.dto.team;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMyTeamMemberRequestDto {

    @Size(max = 1000, message = "Ghi chú nội bộ không được vượt quá 1000 ký tự")
    private String internalNote;

    private Boolean isFavorited;

    private Boolean isBlacklisted;
}
