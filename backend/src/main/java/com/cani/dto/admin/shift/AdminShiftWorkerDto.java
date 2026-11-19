package com.cani.dto.admin.shift;

import com.cani.entity.enums.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminShiftWorkerDto {
    private UUID assignmentId;
    private UUID workerId;
    private UUID userId;
    private String workerName;
    private String workerPhone;
    private String workerEmail;
    private String avatarUrl;
    private AssignmentStatus status;
    private Boolean isGroupLeader;
    private Instant checkinTime;
    private Instant checkoutTime;
    private String attendanceStatus;
    private String checklistProgress;
    private String proofImageUrl;
    private Boolean isOnTime;
    private Boolean eoConfirmed;
    private Instant eoConfirmedAt;
}
