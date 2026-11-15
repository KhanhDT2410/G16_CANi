package com.cani.dto.group;

import com.cani.entity.enums.GroupRole;
import com.cani.entity.enums.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerGroupMemberResponseDto {

    private UUID membershipId;
    private UUID workerId;
    private UUID userId;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private GroupRole roleInGroup;
    private String roleTitle;
    private MembershipStatus status;
    private Integer reliabilityScore;
    private BigDecimal avgRating;
    private Integer totalShiftsCompleted;
    private Instant joinedAt;
}
