package com.cani.dto.group;

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
public class AcceptGroupProposalResponseDto {

    private List<UUID> assignmentIds;
    private UUID shiftId;
    private Integer memberCount;
}
