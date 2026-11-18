package com.cani.dto.group;

import com.cani.entity.enums.AssignmentStatus;
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
public class GroupShiftResponseDto {

    private UUID shiftId;
    private String jobType;
    private String eventName;
    private String venueAddress;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal hourlyWage;
    private Integer totalGroupMembers;
    private Integer confirmedCount;
    private Integer checkedInCount;
    private Integer completedCount;
    private Integer cancelledCount;
    private List<MemberAssignmentDto> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberAssignmentDto {
        private UUID workerId;
        private String fullName;
        private String phone;
        private Boolean isGroupLeader;
        private AssignmentStatus assignmentStatus;
    }
}
