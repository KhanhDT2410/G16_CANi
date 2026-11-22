package com.cani.dto.profile;

import com.cani.dto.event.EoReviewResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EoPublicProfileResponseDto {
    private UUID eoId;
    private UUID userId;
    private String companyName;
    private String representativeName;
    private String avatarUrl;
    private String address;
    private String phone;
    private String email;
    private String description;

    // Tích xanh xác thực pháp lý (thay cho mã số thuế)
    private Boolean isVerified;
    private Instant verifiedAt;

    // Thống kê năng lực & uy tín thực tế từ DB
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer totalEventsCompleted;
    private Integer totalEventsCreated;
    private Integer totalConfirmedWorkers;

    // Lịch sử đánh giá thực tế từ các Worker
    private List<EoReviewResponseDto> reviews;
}
