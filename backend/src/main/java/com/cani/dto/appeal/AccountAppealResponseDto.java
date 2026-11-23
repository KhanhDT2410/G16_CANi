package com.cani.dto.appeal;

import com.cani.entity.enums.AccountAppealStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountAppealResponseDto {

    private UUID id;
    private UUID userId;
    private String email;
    private String userRole;
    private String fullName;
    private String companyName;
    private String reasonCategory;
    private String appealContent;
    private String attachmentUrls;
    private String lockReasonSnapshot;
    private AccountAppealStatus status;
    private UUID reviewerId;
    private String reviewerName;
    private String adminNotes;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
