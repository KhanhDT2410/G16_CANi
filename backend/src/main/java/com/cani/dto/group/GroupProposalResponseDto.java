package com.cani.dto.group;

import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.ProposalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupProposalResponseDto {

    // Thông tin ca làm việc
    private UUID shiftId;
    private String jobType;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal hourlyWage;
    private String eventName;
    private Integer requiredWorkers;
    private Integer remainingWorkers;
    private Boolean emergency;

    // Thông tin đề xuất nhóm
    private UUID proposalId;
    private ProposalStatus status;
    private BigDecimal matchScore;
    private Instant expiresAt;
    private Integer claimableSlots;
    private Integer eligibleCount;
    private List<EligibleMemberDto> eligibleMembers;
    private AssignmentSource source;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligibleMemberDto {
        private UUID workerId;
        private String fullName;
        private Boolean eligible;
        private String reasonCode;
    }
}
