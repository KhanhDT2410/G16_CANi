package com.cani.dto.group;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferLeaderRequestDto {

    @NotNull(message = "Vui lòng chỉ định người tiếp quản Trưởng nhóm")
    private UUID newLeaderWorkerId;
}
