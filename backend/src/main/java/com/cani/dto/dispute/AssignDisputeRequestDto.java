package com.cani.dto.dispute;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request gán / chuyển tranh chấp (SRS UC-99: CC tự nhận hoặc chuyển cho CC khác).
 * {@code targetCcUserId} null = CC tự nhận đơn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignDisputeRequestDto {

    private UUID targetCcUserId;
}
