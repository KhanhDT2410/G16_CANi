package com.cani.dto.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request phân công hoặc nhận xử lý ticket (SCR-25 / SCR-26).
 * targetCcUserId = null nếu CC tự nhận xử lý.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignTicketRequestDto {

    private UUID targetCcUserId;
}

