package com.cani.dto.admin.noshow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoShowStatsDto {

    private long totalNoShows;
    private long noShowsToday;
    private long noShowsThisMonth;
    private long totalRsDeducted;
    private long totalAccountsLocked;
}

