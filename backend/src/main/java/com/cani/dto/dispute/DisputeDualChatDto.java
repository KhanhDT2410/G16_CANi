package com.cani.dto.dispute;

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
public class DisputeDualChatDto {
    private UUID disputeId;
    private String disputeTitle;
    private String disputeStatus;
    private UUID assignmentId;
    private UUID shiftId;
    private String shiftTitle;
    private String eventName;
    private Instant shiftStartTime;
    private Instant shiftEndTime;
    private String currentRole;
    private DisputePartyChatDto workerConversation;
    private DisputePartyChatDto eoConversation;
}
