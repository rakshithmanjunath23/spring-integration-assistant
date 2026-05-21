package com.alip.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Centralized configuration properties for the Spring Integration Assistant.
 * All custom properties use the "assistant" prefix in application.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "assistant")
public class AppProperties {

    private Grok grok = new Grok();
    private Indexing indexing = new Indexing();
    private Rag rag = new Rag();

    @Data
    public static class Grok {
        private String apiUrl = "https://api.x.ai/v1";
        private String apiKey;
        private String model = "grok-3-latest";
        private double temperature = 0.3;
        private int maxTokens = 4096;
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Indexing {
        private String csvPath = "/home/dev/Trunk/trunk/IXconfiguration.txt";
        private String projectRoot = "/home/dev/Trunk/trunk/interfaceexchange/apps/nep-integration-config/src/main";
        private int maxDepth = 20;
        private List<String> excludeDirs = List.of("target", ".git", ".svn", "node_modules", ".idea");
        private long maxFileSize = 10485760; // 10 MB
    }

    @Data
    public static class Rag {
        private int topK = 10;
        private double threshold = 0.7;
        private double boostFactor = 1.5;
        private int maxTokens = 8192;
        private int overlapTokens = 100;
        private int chunkSize = 1000;
    }
}
