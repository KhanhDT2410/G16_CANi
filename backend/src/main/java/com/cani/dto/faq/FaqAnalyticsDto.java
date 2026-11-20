package com.cani.dto.faq;

import com.cani.entity.enums.FaqCategory;
import com.cani.entity.enums.FaqTargetRole;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaqAnalyticsDto {

    private Long totalViews;
    private Map<FaqCategory, Long> viewsByCategory;
    private Map<FaqTargetRole, Long> viewsByTargetRole;
    private List<FaqResponseDto> topFaqs;
    private Double ticketDeflectionRate;
    private String calculationFormula;
}

