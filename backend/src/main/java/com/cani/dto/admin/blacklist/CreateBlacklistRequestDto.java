package com.cani.dto.admin.blacklist;

import com.cani.entity.enums.BlacklistIdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateBlacklistRequestDto {

    @NotBlank(message = "Định danh không được để trống (số CCCD hoặc SĐT)")
    @Size(max = 100, message = "Định danh không vượt quá 100 ký tự")
    private String identifier;

    @NotNull(message = "Loại định danh không được để trống (CCCD hoặc PHONE)")
    private BlacklistIdentifierType identifierType;

    @Builder.Default
    private Boolean permanent = false;

    @Size(max = 50, message = "Loại tham chiếu không vượt quá 50 ký tự")
    private String referenceType;

    private UUID referenceId;

    @NotBlank(message = "Lý do đưa vào danh sách đen không được để trống")
    @Size(max = 1000, message = "Lý do không vượt quá 1000 ký tự")
    private String reason;
}
