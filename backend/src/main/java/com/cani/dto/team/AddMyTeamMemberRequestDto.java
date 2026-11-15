package com.cani.dto.team;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMyTeamMemberRequestDto {

    private UUID workerId;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phone;

    @Size(max = 1000, message = "Ghi chú nội bộ không được vượt quá 1000 ký tự")
    private String internalNote;

    @Builder.Default
    private Boolean isFavorited = false;
}
