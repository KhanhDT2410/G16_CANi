package com.cani.dto.ekyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Báo cáo kiểm kê chỉ đếm số lượng hồ sơ eKYC cũ chưa có hạn lưu giữ ảnh (image_retention_until IS NULL).
 * Không tự động gán hạn hay xóa tệp thật.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EkycLegacyRetentionInventoryDto {
    private long workerLegacyNullRetentionCount;
    private long eoLegacyNullRetentionCount;
    private long totalLegacyNullRetentionCount;
    private Map<String, Long> workerByStatus;
    private Map<String, Long> eoByStatus;
}
