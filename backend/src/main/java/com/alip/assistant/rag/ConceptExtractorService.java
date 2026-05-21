package com.alip.assistant.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and tags Spring Integration concepts in source file content.
 * Identifies integration patterns, protocol types, API URLs, and classifies
 * files by their integration role.
 */
@Slf4j
@Service
public class ConceptExtractorService {

    // Spring Integration concept patterns
    private static final Map<String, List<String>> CONCEPT_PATTERNS = new LinkedHashMap<>();

    // Protocol type patterns
    private static final Map<String, List<String>> PROTOCOL_PATTERNS = new LinkedHashMap<>();

    // URL extraction pattern
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://[^\\s\"'<>]+)|(?:url\\s*=\\s*[\"']([^\"']+)[\"'])|(?:uri\\s*=\\s*[\"']([^\"']+)[\"'])",
            Pattern.CASE_INSENSITIVE
    );

    static {
        // Spring Integration concepts
        CONCEPT_PATTERNS.put("IntegrationFlow", List.of("IntegrationFlow", "IntegrationFlows"));
        CONCEPT_PATTERNS.put("MessageChannel", List.of("MessageChannel", "DirectChannel", "QueueChannel",
                "PublishSubscribeChannel", "int:channel"));
        CONCEPT_PATTERNS.put("Router", List.of("@Router", "int:router", "HeaderValueRouter", "PayloadTypeRouter"));
        CONCEPT_PATTERNS.put("Splitter", List.of("@Splitter", "int:splitter"));
        CONCEPT_PATTERNS.put("Aggregator", List.of("@Aggregator", "int:aggregator"));
        CONCEPT_PATTERNS.put("Gateway", List.of("@MessagingGateway", "int:gateway", "GatewayProxyFactoryBean"));
        CONCEPT_PATTERNS.put("ServiceActivator", List.of("@ServiceActivator", "int:service-activator"));
        CONCEPT_PATTERNS.put("Transformer", List.of("@Transformer", "int:transformer"));
        CONCEPT_PATTERNS.put("Poller", List.of("@Poller", "PollerMetadata", "int:poller"));
        CONCEPT_PATTERNS.put("ErrorChannel", List.of("errorChannel", "int:channel id=\"errorChannel\""));
        CONCEPT_PATTERNS.put("RetryAdvice", List.of("RequestHandlerRetryAdvice", "RetryTemplate", "retry-advice"));
        CONCEPT_PATTERNS.put("XSD", List.of("xsd:", "schema", "SchemaValidation"));

        // Protocol types
        PROTOCOL_PATTERNS.put("Kafka", List.of("kafka:", "KafkaTemplate", "int-kafka:"));
        PROTOCOL_PATTERNS.put("JMS", List.of("int-jms:", "JmsTemplate", "jms:"));
        PROTOCOL_PATTERNS.put("HTTP", List.of("int-http:", "HttpRequestHandlingMessagingGateway", "http:"));
        PROTOCOL_PATTERNS.put("SFTP", List.of("int-sftp:", "SftpRemoteFileTemplate", "sftp:"));
        PROTOCOL_PATTERNS.put("SOAP", List.of("int-ws:", "WebServiceGateway", "ws:"));
        PROTOCOL_PATTERNS.put("REST", List.of("@RestController", "@RequestMapping", "RestTemplate"));
    }

    /**
     * Extract Spring Integration concepts and protocol types from file content.
     *
     * @param content  the source file content to analyze
     * @param fileType the file type (java, xml, yaml, properties, etc.)
     * @return list of detected concept names
     */
    public List<String> extractConcepts(String content, String fileType) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> detectedConcepts = new ArrayList<>();

        // Detect Spring Integration concepts
        for (Map.Entry<String, List<String>> entry : CONCEPT_PATTERNS.entrySet()) {
            String conceptName = entry.getKey();
            List<String> patterns = entry.getValue();
            if (matchesAnyPattern(content, patterns)) {
                detectedConcepts.add(conceptName);
            }
        }

        // Detect protocol types
        for (Map.Entry<String, List<String>> entry : PROTOCOL_PATTERNS.entrySet()) {
            String protocolName = entry.getKey();
            List<String> patterns = entry.getValue();
            if (matchesAnyPattern(content, patterns)) {
                detectedConcepts.add(protocolName);
            }
        }

        log.debug("Extracted {} concepts from {} file: {}", detectedConcepts.size(), fileType, detectedConcepts);
        return detectedConcepts;
    }

    /**
     * Classify a file by its integration role based on namespace declarations,
     * annotations, and content patterns.
     *
     * @param content  the source file content to analyze
     * @param fileType the file type (java, xml, yaml, properties, etc.)
     * @return the classified integration role (inbound-adapter, outbound-adapter,
     *         transformer, router, gateway) or "unknown" if no role can be determined
     */
    public String classifyIntegrationRole(String content, String fileType) {
        if (content == null || content.isBlank()) {
            return "unknown";
        }

        // Check for gateway role (highest priority - explicit gateway declarations)
        if (containsAny(content, "@MessagingGateway", "int:gateway", "GatewayProxyFactoryBean",
                "HttpRequestHandlingMessagingGateway", "WebServiceGateway")) {
            return "gateway";
        }

        // Check for router role
        if (containsAny(content, "@Router", "int:router", "HeaderValueRouter", "PayloadTypeRouter")) {
            return "router";
        }

        // Check for transformer role
        if (containsAny(content, "@Transformer", "int:transformer")) {
            return "transformer";
        }

        // Check for inbound adapter role
        if (containsAny(content, "int:inbound-channel-adapter", "inbound-channel-adapter",
                "int-jms:message-driven-channel-adapter", "int-http:inbound-gateway",
                "int-sftp:inbound-channel-adapter", "int-kafka:message-driven-channel-adapter",
                "int-ws:inbound-gateway")) {
            return "inbound-adapter";
        }

        // Check for outbound adapter role
        if (containsAny(content, "int:outbound-channel-adapter", "outbound-channel-adapter",
                "int-jms:outbound-channel-adapter", "int-http:outbound-gateway",
                "int-sftp:outbound-channel-adapter", "int-kafka:outbound-channel-adapter",
                "int-ws:outbound-gateway")) {
            return "outbound-adapter";
        }

        return "unknown";
    }

    /**
     * Extract API URLs from file content. Matches http://, https:// URLs,
     * and url="..." or uri="..." attribute patterns.
     *
     * @param content the source file content to analyze
     * @return list of extracted URL strings
     */
    public List<String> extractApiUrls(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(content);

        while (matcher.find()) {
            String url = matcher.group(0);
            // Check for url="..." or uri="..." capture groups
            if (matcher.group(1) != null) {
                url = matcher.group(1);
            } else if (matcher.group(2) != null) {
                url = matcher.group(2);
            }
            // Clean up trailing punctuation that might be part of surrounding text
            url = url.replaceAll("[\"'<>\\s]+$", "");
            if (!url.isBlank() && !urls.contains(url)) {
                urls.add(url);
            }
        }

        log.debug("Extracted {} API URLs from content", urls.size());
        return urls;
    }

    /**
     * Check if content contains any of the given patterns (case-sensitive).
     */
    private boolean matchesAnyPattern(String content, List<String> patterns) {
        for (String pattern : patterns) {
            if (content.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if content contains any of the given strings.
     */
    private boolean containsAny(String content, String... patterns) {
        for (String pattern : patterns) {
            if (content.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
