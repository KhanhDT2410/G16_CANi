package com.cani.dto.dispute;

import com.cani.entity.enums.DisputeEvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request nạp bằng chứng cho một tranh chấp (SRS UC-98).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddDisputeEvidenceRequestDto {

    @NotNull(message = "Loại bằng chứng không được để trống")
    private DisputeEvidenceType fileType;

    @NotBlank(message = "Đường dẫn file bằng chứng không được để trống")
    @Size(max = 500, message = "Đường dẫn file không được vượt quá 500 ký tự")
    private String fileUrl;

    @Size(max = 255, message = "Nhãn bằng chứng không được vượt quá 255 ký tự")
    private String label;
}
