package com.cani.dto.admin.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponseDto {

    private UUID id;
    private String key;
    private String value;
    private String description;
    private Instant updatedAt;
    private UUID updatedBy;
}

