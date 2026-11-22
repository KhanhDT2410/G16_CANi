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
public class GroupProposalAcceptRequestDto {

    /**
     * Danh sách workerId thành viên được chọn.
     * Để trống hoặc null: Chấp nhận ca TRỌN GÓI cho toàn bộ thành viên ACTIVE của đội (Squad Pack).
     */
    private List<UUID> memberWorkerIds;
}
