package com.alip.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Legacy configuration class retained for backward compatibility.
 * Delegates to AppProperties for the canonical configuration.
 *
 * Existing services that inject AppConfig will continue to work.
 * New code should inject AppProperties directly.
 *
 * @deprecated Use {@link AppProperties} instead.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
@Deprecated
public class AppConfig {
    private String sourceProjectPath;
    private String ixConfigCsvPath;
    private GrokConfig grok = new GrokConfig();
    private RagConfig rag = new RagConfig();

    @Data
    public static class GrokConfig {
        private String apiKey;
        private String apiUrl;
        private String model;
        private double temperature = 0.3;
        private int maxTokens = 4096;
    }

    @Data
    public static class RagConfig {
        private int chunkSize = 1000;
        private int chunkOverlap = 200;
        private int maxResults = 5;
        private double similarityThreshold = 0.7;
    }
}
