package com.cani.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EoPublicProfileResponseDto {
    private UUID eoId;
    private UUID userId;
    private String companyName;
    private String avatarUrl;
    private String address;
    private String phone;
    private String email;
    private String description;

    // Tích xanh xác thực pháp lý (thay cho mã số thuế)
    private Boolean isVerified;

    // Thống kê năng lực & uy tín
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer totalEventsCompleted;
}
