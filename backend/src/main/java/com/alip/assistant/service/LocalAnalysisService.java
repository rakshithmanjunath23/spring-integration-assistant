package com.alip.assistant.service;

import com.alip.assistant.rag.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Local analysis engine that generates answers from indexed source code
 * without requiring an external LLM API.
 * Uses pattern matching and code analysis on RAG-retrieved chunks.
 */
@Slf4j
@Service
public class LocalAnalysisService {

    public String generateResponse(String question, List<DocumentChunk> relevantChunks) {
        if (relevantChunks == null || relevantChunks.isEmpty()) {
            return "I couldn't find relevant source code for your question. "
                    + "Try selecting specific files from the sidebar or rephrase your question.\n\n"
                    + "**Tip:** Make sure the project has been indexed (check the status badge in the top bar).";
        }

        String lowerQuestion = question.toLowerCase();
        StringBuilder response = new StringBuilder();

        // Determine question type and generate appropriate response
        if (lowerQuestion.contains("list") || lowerQuestion.contains("all") || lowerQuestion.contains("how many")) {
            response.append(generateListResponse(question, relevantChunks));
        } else if (lowerQuestion.contains("explain") || lowerQuestion.contains("what is") || lowerQuestion.contains("describe")) {
            response.append(generateExplanationResponse(question, relevantChunks));
        } else if (lowerQuestion.contains("flow") || lowerQuestion.contains("trace") || lowerQuestion.contains("path")) {
            response.append(generateFlowResponse(question, relevantChunks));
        } else if (lowerQuestion.contains("error") || lowerQuestion.contains("fail") || lowerQuestion.contains("exception")) {
            response.append(generateErrorAnalysisResponse(question, relevantChunks));
        } else if (lowerQuestion.contains("channel") || lowerQuestion.contains("bean") || lowerQuestion.contains("config")) {
            response.append(generateConfigResponse(question, relevantChunks));
        } else {
            response.append(generateGeneralResponse(question, relevantChunks));
        }

        // Add source citations
        response.append("\n\n---\n**📎 Sources analyzed:**\n");
        Set<String> cited = new LinkedHashSet<>();
        for (DocumentChunk chunk : relevantChunks) {
            if (cited.add(chunk.getFileName())) {
                response.append("- `").append(chunk.getFileName()).append("`")
                        .append(" (relevance: ").append(Math.round(chunk.getScore() * 100)).append("%)\n");
            }
        }

        return response.toString();
    }

    private String generateListResponse(String question, List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Analysis Results\n\n");

        // Extract beans, channels, integrations from chunks
        List<String> beans = new ArrayList<>();
        List<String> channels = new ArrayList<>();
        List<String> integrations = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();

            // Extract @Bean methods
            Pattern beanPattern = Pattern.compile("public\\s+\\w+\\s+(\\w+)\\s*\\(");
            Matcher beanMatcher = beanPattern.matcher(content);
            while (beanMatcher.find()) {
                beans.add(beanMatcher.group(1));
            }

            // Extract channel definitions
            Pattern channelPattern = Pattern.compile("id=\"([^\"]*[Cc]hannel[^\"]*)\"");
            Matcher channelMatcher = channelPattern.matcher(content);
            while (channelMatcher.find()) {
                channels.add(channelMatcher.group(1));
            }

            // Extract integration names from CSV-like content
            if (content.contains(",inbound") || content.contains(",outbound")) {
                String[] lines = content.split("\n");
                for (String line : lines) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        integrations.add(parts[0].trim() + " (" + parts[2].trim() + ")");
                    }
                }
            }

            // Extract ChildConfiguration beans
            Pattern childConfigPattern = Pattern.compile("ChildConfiguration\\s+(\\w+)\\(\\)");
            Matcher childMatcher = childConfigPattern.matcher(content);
            while (childMatcher.find()) {
                integrations.add(childMatcher.group(1));
            }
        }

        if (!integrations.isEmpty()) {
            sb.append("### Integrations Found\n\n");
            integrations.stream().distinct().limit(30).forEach(i -> sb.append("- `").append(i).append("`\n"));
            if (integrations.size() > 30) sb.append("- ... and ").append(integrations.size() - 30).append(" more\n");
        }
        if (!beans.isEmpty()) {
            sb.append("\n### Beans Found\n\n");
            beans.stream().distinct().limit(20).forEach(b -> sb.append("- `").append(b).append("`\n"));
        }
        if (!channels.isEmpty()) {
            sb.append("\n### Channels Found\n\n");
            channels.stream().distinct().limit(20).forEach(c -> sb.append("- `").append(c).append("`\n"));
        }

        if (integrations.isEmpty() && beans.isEmpty() && channels.isEmpty()) {
            sb.append("Based on the relevant source files, here's what I found:\n\n");
            for (DocumentChunk chunk : chunks) {
                sb.append("**From `").append(chunk.getFileName()).append("`:**\n");
                sb.append("```").append(chunk.getFileType()).append("\n");
                sb.append(truncate(chunk.getContent(), 500));
                sb.append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    private String generateExplanationResponse(String question, List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();

        // Try to identify the specific integration being asked about
        String target = extractTarget(question);
        sb.append("## ").append(target != null ? target : "Integration Analysis").append("\n\n");

        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();

            // Extract key information
            if (chunk.getFileType().equals("java")) {
                sb.append("**From `").append(chunk.getFileName()).append("` (Java):**\n\n");

                // Find ConditionalOnProperty
                Pattern propPattern = Pattern.compile("prefix\\s*=\\s*\"([^\"]+)\"");
                Matcher propMatcher = propPattern.matcher(content);
                if (propMatcher.find()) {
                    sb.append("- **Property prefix:** `").append(propMatcher.group(1)).append("`\n");
                }

                // Find resource path
                Pattern resPattern = Pattern.compile("classpath:([^\"]+)");
                Matcher resMatcher = resPattern.matcher(content);
                if (resMatcher.find()) {
                    sb.append("- **Configuration XML:** `").append(resMatcher.group(1)).append("`\n");
                }

                // Determine direction
                if (content.contains("inbound")) sb.append("- **Direction:** Inbound\n");
                else if (content.contains("outbound")) sb.append("- **Direction:** Outbound\n");

                sb.append("\n```java\n").append(truncate(content, 600)).append("\n```\n\n");

            } else if (chunk.getFileType().equals("xml")) {
                sb.append("**From `").append(chunk.getFileName()).append("` (XML):**\n\n");

                // Extract bean IDs
                Pattern idPattern = Pattern.compile("(?:id|name)=\"([^\"]+)\"");
                Matcher idMatcher = idPattern.matcher(content);
                List<String> ids = new ArrayList<>();
                while (idMatcher.find()) ids.add(idMatcher.group(1));
                if (!ids.isEmpty()) {
                    sb.append("- **Beans defined:** ");
                    sb.append(ids.stream().limit(5).map(i -> "`" + i + "`").collect(Collectors.joining(", ")));
                    sb.append("\n");
                }

                sb.append("\n```xml\n").append(truncate(content, 600)).append("\n```\n\n");
            } else {
                sb.append("**From `").append(chunk.getFileName()).append("`:**\n\n");
                sb.append("```\n").append(truncate(content, 400)).append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    private String generateFlowResponse(String question, List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Message Flow Analysis\n\n");

        List<String> flowSteps = new ArrayList<>();
        Set<String> channels = new LinkedHashSet<>();
        Set<String> endpoints = new LinkedHashSet<>();

        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();

            // Extract channels
            Pattern chPattern = Pattern.compile("(?:channel|Channel).*?[\"=]([\\w.-]+)");
            Matcher chMatcher = chPattern.matcher(content);
            while (chMatcher.find()) channels.add(chMatcher.group(1));

            // Extract service activators
            Pattern saPattern = Pattern.compile("service-activator.*?ref=\"([^\"]+)\"");
            Matcher saMatcher = saPattern.matcher(content);
            while (saMatcher.find()) endpoints.add("ServiceActivator: " + saMatcher.group(1));

            // Extract gateways
            Pattern gwPattern = Pattern.compile("gateway.*?service-interface=\"([^\"]+)\"");
            Matcher gwMatcher = gwPattern.matcher(content);
            while (gwMatcher.find()) endpoints.add("Gateway: " + gwMatcher.group(1));
        }

        if (!channels.isEmpty()) {
            sb.append("### Channels Involved\n");
            channels.forEach(c -> sb.append("- `").append(c).append("`\n"));
            sb.append("\n");
        }

        if (!endpoints.isEmpty()) {
            sb.append("### Endpoints\n");
            endpoints.forEach(e -> sb.append("- ").append(e).append("\n"));
            sb.append("\n");
        }

        sb.append("### Relevant Source Code\n\n");
        for (DocumentChunk chunk : chunks) {
            sb.append("**`").append(chunk.getFileName()).append("`:**\n");
            sb.append("```").append(chunk.getFileType()).append("\n");
            sb.append(truncate(chunk.getContent(), 500));
            sb.append("\n```\n\n");
        }

        return sb.toString();
    }

    private String generateErrorAnalysisResponse(String question, List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Error Handling Analysis\n\n");

        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();
            if (content.contains("error") || content.contains("Error") || content.contains("exception")
                    || content.contains("retry") || content.contains("Retry")) {
                sb.append("**From `").append(chunk.getFileName()).append("`:**\n");
                sb.append("```").append(chunk.getFileType()).append("\n");
                sb.append(truncate(content, 600));
                sb.append("\n```\n\n");
            }
        }

        if (sb.toString().equals("## Error Handling Analysis\n\n")) {
            sb.append("No specific error handling patterns found in the retrieved chunks. ");
            sb.append("Try asking about specific error channels or retry configurations.\n\n");
            sb.append("### Related source:\n\n");
            for (DocumentChunk chunk : chunks) {
                sb.append("- `").append(chunk.getFileName()).append("`\n");
            }
        }

        return sb.toString();
    }

    private String generateConfigResponse(String question, List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Configuration Analysis\n\n");

        for (DocumentChunk chunk : chunks) {
            sb.append("**`").append(chunk.getFileName()).append("`** (").append(chunk.getFileType()).append("):\n");
            sb.append("```").append(chunk.getFileType()).append("\n");
            sb.append(truncate(chunk.getContent(), 700));
            sb.append("\n```\n\n");
        }

        return sb.toString();
    }

    private String generateGeneralResponse(String question, List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Analysis\n\n");
        sb.append("Based on the indexed source code, here's what I found relevant to your question:\n\n");

        for (DocumentChunk chunk : chunks) {
            sb.append("### `").append(chunk.getFileName()).append("`\n\n");
            sb.append("```").append(chunk.getFileType()).append("\n");
            sb.append(truncate(chunk.getContent(), 600));
            sb.append("\n```\n\n");
        }

        return sb.toString();
    }

    private String extractTarget(String question) {
        // Try to find a specific integration name in the question
        Pattern pattern = Pattern.compile("\\b([a-z][a-zA-Z]+(?:Inquiry|Detail|Transfer|Outbound|Inbound|Processing|Upload|Import|Update|Create|Delete|Search|Get|Retrieve))\\b");
        Matcher matcher = pattern.matcher(question);
        if (matcher.find()) return matcher.group(1);

        // Try camelCase words
        Pattern camelPattern = Pattern.compile("\\b([a-z]+[A-Z][a-zA-Z]+)\\b");
        Matcher camelMatcher = camelPattern.matcher(question);
        if (camelMatcher.find()) return camelMatcher.group(1);

        return null;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "\n... (truncated)";
    }
}
