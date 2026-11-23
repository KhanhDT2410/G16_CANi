package com.cani.dto.ekyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EkycConsentPolicyDto {
    private boolean required;
    private String noticeVersion;
    private String title;
    private String summary;
}
