package com.cani.dto.admin.blacklist;

import com.cani.entity.enums.BlacklistIdentifierType;
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
public class BlacklistResponseDto {

    private UUID id;
    private String identifier;
    private BlacklistIdentifierType identifierType;
    private Boolean permanent;
    private String referenceType;
    private UUID referenceId;
    private String reason;
    private UUID createdById;
    private String createdByName;
    private Instant createdAt;
}
