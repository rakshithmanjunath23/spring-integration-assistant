package com.alip.assistant.rag;

import com.alip.assistant.config.AppProperties;
import com.alip.assistant.dto.SourceCitation;
import com.alip.assistant.repository.ChatMessageEntity;
import com.alip.assistant.util.TokenCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles the full prompt for the LLM: system instructions + retrieved chunks
 * + conversation history + user query. Enforces token budget by truncating
 * lowest-relevance chunks first, then oldest history messages.
 * System prompt and user query are never truncated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptAssemblerService {

    private final VectorStoreService vectorStoreService;
    private final TokenCounter tokenCounter;
    private final AppProperties appProperties;

    private static final String SYSTEM_PROMPT =
            "You are an expert Spring Integration architecture assistant. You analyze enterprise Spring Integration projects "
            + "and provide detailed, accurate answers about:\n"
            + "- Message flows and channel configurations\n"
            + "- Integration patterns (routers, splitters, aggregators, transformers)\n"
            + "- Service activators and gateways\n"
            + "- JMS, HTTP, SOAP, SFTP adapters\n"
            + "- Error handling and retry mechanisms\n"
            + "- Bean configurations and dependencies\n"
            + "- Java DSL and XML-based Spring Integration configurations\n"
            + "- ACORD/ALIP insurance platform integrations\n\n"
            + "When answering:\n"
            + "1. Always cite the source files you reference using [Source N: filename] format\n"
            + "2. Be precise about bean names, channel names, and configuration details\n"
            + "3. Explain the message flow step by step when asked\n"
            + "4. Identify potential issues or improvements when relevant\n"
            + "5. Use code snippets from the actual source when helpful\n"
            + "6. If you cannot find relevant information in the provided context, say so clearly\n\n"
            + "Format your answers in Markdown for readability.\n"
            + "If you reference source files, include them as citations at the end.";

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_CITATIONS = 10;

    /**
     * Assemble the full prompt from user query, selected files, and conversation history.
     * Respects token budget, truncating lowest-relevance chunks first, then oldest history.
     *
     * @param userQuery     the user's current question
     * @param selectedFiles list of file paths the user has selected for priority
     * @param history       recent conversation history messages
     * @return an AssembledPrompt containing all components ready for LLM consumption
     */
    public AssembledPrompt assemble(String userQuery, List<String> selectedFiles,
                                     List<ChatMessageEntity> history) {
        int maxTokens = appProperties.getRag().getMaxTokens();

        // Step 1: Calculate fixed token costs (system prompt + user query - never truncated)
        int systemTokens = tokenCounter.estimateTokens(SYSTEM_PROMPT);
        int queryTokens = tokenCounter.estimateTokens(userQuery);
        int fixedTokens = systemTokens + queryTokens;

        // Step 2: Retrieve relevant chunks from vector store
        List<DocumentChunk> retrievedChunks = vectorStoreService.search(userQuery, selectedFiles);

        // Step 3: Prepare conversation history (last N messages)
        List<ChatMessageEntity> recentHistory = trimHistory(history);

        // Step 4: Budget allocation - fill remaining budget with chunks then history
        int remainingBudget = maxTokens - fixedTokens;

        // Fit chunks within budget (remove lowest relevance first)
        List<DocumentChunk> fittedChunks = fitChunksWithinBudget(retrievedChunks, remainingBudget);
        String contextSection = formatContextSection(fittedChunks);
        int contextTokens = tokenCounter.estimateTokens(contextSection);

        // Fit history within remaining budget after chunks
        int historyBudget = remainingBudget - contextTokens;
        List<Map<String, String>> fittedHistory = fitHistoryWithinBudget(recentHistory, historyBudget);

        // Step 5: Build citations (deduplicated by file path, max 10, ordered by score)
        List<SourceCitation> citations = buildCitations(fittedChunks);

        // Step 6: Calculate total estimated tokens
        int historyTokens = fittedHistory.stream()
                .mapToInt(msg -> tokenCounter.estimateTokens(msg.get("content")))
                .sum();
        int totalTokens = fixedTokens + contextTokens + historyTokens;

        log.debug("Assembled prompt: system={}t, context={}t ({} chunks), history={}t ({} msgs), query={}t, total={}t (budget={}t)",
                systemTokens, contextTokens, fittedChunks.size(),
                historyTokens, fittedHistory.size(),
                queryTokens, totalTokens, maxTokens);

        return new AssembledPrompt(
                SYSTEM_PROMPT,
                contextSection,
                fittedHistory,
                userQuery,
                citations,
                totalTokens
        );
    }

    /**
     * Trim history to the most recent MAX_HISTORY_MESSAGES messages.
     */
    private List<ChatMessageEntity> trimHistory(List<ChatMessageEntity> history) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        return history.subList(start, history.size());
    }

    /**
     * Fit chunks within the token budget by removing lowest-relevance chunks first.
     * Chunks are already sorted by descending score from the vector store.
     */
    private List<DocumentChunk> fitChunksWithinBudget(List<DocumentChunk> chunks, int budgetTokens) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<DocumentChunk> fitted = new ArrayList<>();
        int usedTokens = 0;

        for (DocumentChunk chunk : chunks) {
            String formatted = formatSingleChunk(chunk);
            int chunkTokens = tokenCounter.estimateTokens(formatted);

            if (usedTokens + chunkTokens <= budgetTokens) {
                fitted.add(chunk);
                usedTokens += chunkTokens;
            } else {
                // Budget exceeded - stop adding chunks (they're already sorted by relevance)
                log.debug("Token budget reached after {} chunks (used {}t of {}t budget)",
                        fitted.size(), usedTokens, budgetTokens);
                break;
            }
        }

        return fitted;
    }

    /**
     * Fit conversation history within the token budget by removing oldest messages first.
     */
    private List<Map<String, String>> fitHistoryWithinBudget(List<ChatMessageEntity> history, int budgetTokens) {
        if (history == null || history.isEmpty() || budgetTokens <= 0) {
            return Collections.emptyList();
        }

        // Convert all history to maps first
        List<Map<String, String>> allHistory = history.stream()
                .map(msg -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("role", msg.getRole());
                    entry.put("content", msg.getContent());
                    return entry;
                })
                .collect(Collectors.toList());

        // Remove oldest messages until we fit within budget
        int totalTokens = allHistory.stream()
                .mapToInt(msg -> tokenCounter.estimateTokens(msg.get("content")))
                .sum();

        while (totalTokens > budgetTokens && !allHistory.isEmpty()) {
            Map<String, String> removed = allHistory.remove(0); // remove oldest
            totalTokens -= tokenCounter.estimateTokens(removed.get("content"));
        }

        return allHistory;
    }

    /**
     * Format the context section from retrieved chunks.
     */
    private String formatContextSection(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### Relevant Source Context:\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            sb.append(formatSingleChunk(chunk, i + 1));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Format a single chunk for token estimation (without index number).
     */
    private String formatSingleChunk(DocumentChunk chunk) {
        return formatSingleChunk(chunk, 0);
    }

    /**
     * Format a single chunk with source index for display.
     */
    private String formatSingleChunk(DocumentChunk chunk, int sourceIndex) {
        StringBuilder sb = new StringBuilder();
        String header = sourceIndex > 0
                ? String.format("**[Source %d: %s", sourceIndex, chunk.getFileName())
                : String.format("**[%s", chunk.getFileName());

        if (chunk.getIntegrationName() != null && !chunk.getIntegrationName().isEmpty()) {
            header += " | Integration: " + chunk.getIntegrationName();
        }
        if (chunk.getStartLine() > 0 && chunk.getEndLine() > 0) {
            header += " | Lines: " + chunk.getStartLine() + "-" + chunk.getEndLine();
        }
        header += "]**\n";

        sb.append(header);
        sb.append("```").append(chunk.getFileType() != null ? chunk.getFileType() : "").append("\n");
        sb.append(chunk.getContent());
        sb.append("\n```\n");

        return sb.toString();
    }

    /**
     * Build deduplicated citations from retrieved chunks.
     * Keeps highest-scoring entry per file path, limited to MAX_CITATIONS, ordered by descending score.
     */
    private List<SourceCitation> buildCitations(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        // Deduplicate by file path, keeping highest score
        Map<String, SourceCitation> citationMap = new LinkedHashMap<>();
        for (DocumentChunk chunk : chunks) {
            String filePath = chunk.getFilePath();
            if (filePath == null || filePath.isEmpty()) continue;

            SourceCitation existing = citationMap.get(filePath);
            if (existing == null || chunk.getScore() > existing.getRelevanceScore()) {
                String lineRange = null;
                if (chunk.getStartLine() > 0 && chunk.getEndLine() > 0) {
                    lineRange = chunk.getStartLine() + "-" + chunk.getEndLine();
                }
                citationMap.put(filePath, SourceCitation.builder()
                        .filePath(filePath)
                        .integrationName(chunk.getIntegrationName())
                        .relevanceScore(chunk.getScore())
                        .lineRange(lineRange)
                        .build());
            }
        }

        // Sort by descending relevance score and limit to MAX_CITATIONS
        return citationMap.values().stream()
                .sorted(Comparator.comparingDouble(SourceCitation::getRelevanceScore).reversed())
                .limit(MAX_CITATIONS)
                .collect(Collectors.toList());
    }
}
