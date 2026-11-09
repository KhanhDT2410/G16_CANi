package com.cani.dto.admin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateSystemConfigRequestDto {

    /**
     * Cập nhật theo dạng key-value map: {"commission_rate": "0.05", "min_registration_age": "18"}
     */
    private Map<String, String> configs;

    /**
     * Cập nhật theo dạng danh sách item chi tiết kèm mô tả tùy chọn:
     * [{"key": "commission_rate", "value": "0.05", "description": "Tỷ lệ hoa hồng 5%"}]
     */
    @Valid
    private List<ConfigItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigItem {
        @NotBlank(message = "Key cấu hình không được để trống")
        private String key;

        @NotBlank(message = "Giá trị cấu hình không được để trống")
        private String value;

        private String description;
    }

    /**
     * Trả về danh sách cấu hình cần cập nhật dạng Map(key -> ConfigItem).
     */
    public Map<String, ConfigItem> getNormalizedConfigItems() {
        Map<String, ConfigItem> result = new LinkedHashMap<>();

        if (configs != null) {
            for (Map.Entry<String, String> entry : configs.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank()) {
                    result.put(entry.getKey(), ConfigItem.builder()
                            .key(entry.getKey())
                            .value(entry.getValue())
                            .build());
                }
            }
        }

        if (items != null) {
            for (ConfigItem item : items) {
                if (item != null && item.getKey() != null && !item.getKey().isBlank()) {
                    result.put(item.getKey(), item);
                }
            }
        }

        return result;
    }

    public boolean isEmpty() {
        return getNormalizedConfigItems().isEmpty();
    }
}

