package com.cani.dto.ekyc;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EkycAdminReviewRequestDto {

    /**
     * APPROVED hoặc REJECTED
     */
    @NotBlank(message = "Quyết định phê duyệt không được để trống (APPROVED hoặc REJECTED)")
    private String decision;

    private String reviewNotes;
}
