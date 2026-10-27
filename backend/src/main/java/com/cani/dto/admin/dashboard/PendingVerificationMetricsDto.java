package com.cani.dto.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingVerificationMetricsDto {

    /**
     * Số lượng hồ sơ eKYC Worker đang chờ Admin thẩm định
     */
    private long pendingWorkerEkycCount;

    /**
     * Số lượng hồ sơ eKYC Doanh nghiệp (Event Organizer) đang chờ Admin thẩm định
     */
    private long pendingEoEkycCount;

    /**
     * Số lượng yêu cầu thẩm định chứng chỉ kỹ năng Worker đang chờ xử lý
     */
    private long pendingSkillVerificationsCount;

    /**
     * Tổng số lượng hồ sơ / yêu cầu chờ thẩm định
     */
    private long totalPendingCount;
}
