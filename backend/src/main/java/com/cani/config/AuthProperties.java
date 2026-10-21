package com.cani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cani.auth.supabase")
public class AuthProperties {
    private String url;
    private String anonKey;
    private String serviceRoleKey;
}
