package com.cani.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupabaseUser {
    private UUID id;
    private String email;
    private String phone;

    @JsonProperty("user_metadata")
    private Map<String, Object> userMetadata;

    @JsonProperty("email_confirmed_at")
    private String emailConfirmedAt;
}
