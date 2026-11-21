package com.cani.dto.group;

import jakarta.validation.constraints.NotEmpty;
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
public class GroupProposalAcceptRequestDto {

    @NotEmpty(message = "Vui lòng chọn ít nhất một thành viên trong nhóm")
    private List<UUID> memberWorkerIds;
}
