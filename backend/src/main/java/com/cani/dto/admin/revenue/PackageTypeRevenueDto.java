package com.cani.dto.admin.revenue;

import com.cani.entity.enums.PlanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageTypeRevenueDto {
    private PlanType planType;
    private String planTypeName;
    private long salesCount;
    private BigDecimal totalRevenue;
    private Double percentageOfRevenue;
}

