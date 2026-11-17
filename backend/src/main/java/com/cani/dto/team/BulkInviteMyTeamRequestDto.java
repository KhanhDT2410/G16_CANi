package com.cani.dto.team;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkInviteMyTeamRequestDto {

    @NotNull(message = "ID ca làm việc không được để trống")
    private UUID shiftId;

    private List<UUID> workerIds;

    private String message;
}
