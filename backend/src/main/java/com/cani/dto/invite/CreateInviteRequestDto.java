package com.cani.dto.invite;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInviteRequestDto {

    @Min(value = 1, message = "Số lượt tham gia tối đa ít nhất là 1")
    @Max(value = 500, message = "Số lượt tham gia tối đa không quá 500")
    @Builder.Default
    private Integer maxUses = 10;

    @Min(value = 1, message = "Thời hạn hiệu lực ít nhất là 1 giờ")
    @Max(value = 720, message = "Thời hạn hiệu lực tối đa là 720 giờ (30 ngày)")
    @Builder.Default
    private Integer expiryHours = 48;
}

