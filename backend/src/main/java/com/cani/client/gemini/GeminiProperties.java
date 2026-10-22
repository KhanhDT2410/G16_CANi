package com.cani.client.gemini;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cani.ai.gemini")
@Getter
@Setter
public class GeminiProperties {

    /**
     * Google AI Studio API Key. Supplied via GEMINI_API_KEY env var only - never hardcode.
     */
    private String apiKey;

    /**
     * Model name (e.g., gemini-3.5-flash-lite)
     */
    private String model = "gemini-3.5-flash-lite";

    /**
     * API base URL
     */
    private String url = "https://generativelanguage.googleapis.com/v1beta/models";

    /**
     * Request timeout in milliseconds
     */
    private int timeoutMs = 20000;
}
